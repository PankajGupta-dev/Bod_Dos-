import asyncio
import json
import logging
import threading
import time
from datetime import datetime
from typing import Dict, Any, Optional, List
from collections import deque

try:
    import websockets
except ImportError:
    websockets = None

try:
    import httpx
except ImportError:
    httpx = None

logger = logging.getLogger("bsc.sentinel")

class SentinelBridge:
    """
    High-performance Sentinel Bridge with HTTP fallback.
    Runs in a dedicated background thread with an isolated event loop
    to ensure zero-latency impact on AI inference and FastAPI dashboard.
    
    Resilience features:
      - Primary: WebSocket persistent connection
      - Fallback: HTTP POST when WS is unavailable
      - Auto-reconnect with exponential backoff
      - Failed event recovery on reconnection
    """
    
    def __init__(self, ws_url: str):
        self.ws_url = ws_url
        # Derive HTTP fallback URL from WS URL
        self.http_url = ws_url.replace("wss://", "https://").replace("ws://", "http://").rstrip("/ws") + "/api/alerts"
        self._queue = None # Initialized in thread
        self._loop = None
        self._is_running = False
        self._ws = None
        self._thread = None
        self._failed_queue = deque(maxlen=100) # Memory-safe cleanup
        self._http_client = None
        
        # Connection metrics
        self._ws_connected = False
        self._total_sent = 0
        self._total_failed = 0
        self._total_http_fallback = 0
        self._last_error = None
        self._reconnect_delay = 1.0  # Exponential backoff start
        self._last_connected_at: Optional[str] = None
        self._last_sent_at: Optional[str] = None
        self._connection_attempts = 0
        
    def start_dedicated(self):
        """Initializes and starts the bridge in a dedicated OS thread."""
        if self._is_running:
            return
        self._is_running = True
        self._thread = threading.Thread(target=self._run_loop, name="sentinel-bridge-thread", daemon=True)
        self._thread.start()
        logger.info("Sentinel Bridge dedicated thread initialized.")

    def _run_loop(self):
        """Thread entry point: creates and runs a private event loop."""
        self._loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self._loop)
        
        # Initialize queue within this loop
        self._queue = asyncio.Queue()
        
        # Initialize HTTP client for fallback
        if httpx:
            self._http_client = httpx.AsyncClient(timeout=10.0, verify=False)
        
        # Start core tasks
        self._loop.create_task(self._connection_manager())
        self._loop.create_task(self._worker())
        self._loop.create_task(self._keepalive_pinger())
        
        try:
            self._loop.run_forever()
        except Exception as e:
            logger.error(f"Sentinel Bridge loop error: {e}")

    def stop(self):
        """Thread-safe stop — safe to call from any thread."""
        self._is_running = False
        if self._loop and self._loop.is_running():
            # Schedule cleanup inside the bridge thread's own event loop
            async def _cleanup():
                if self._http_client:
                    await self._http_client.aclose()
                self._loop.stop()
            asyncio.run_coroutine_threadsafe(_cleanup(), self._loop)
        logger.info("Sentinel Bridge stopping...")

    def emit_alert(self, event_payload: Dict[str, Any]):
        """
        Thread-safe entry point. 
        Uses call_soon_threadsafe for sub-millisecond handoff.
        """
        if not self._is_running or self._loop is None or self._queue is None:
            return

        # Prepare payload immediately to avoid closure issues
        # Map fields from both Engine (camera_id) and Router (camera) formats
        camera_id = event_payload.get("camera_id") or event_payload.get("camera") or "UNKNOWN"
        alert_type = event_payload.get("alert_type", "UNKNOWN_PERSON")
        
        # Normalize alert_type if it comes from the router (which might use 'critical'/'warning')
        if alert_type == "critical":
            alert_type = "CRITICAL_ALERT"
        elif alert_type == "warning":
            alert_type = "THREAT_WARNING"

        payload = {
            "event_id": event_payload.get("event_id") or event_payload.get("id") or f"EVT-{time.time_ns()}",
            "camera_id": camera_id,
            "alert_type": alert_type,
            "unknown_id": event_payload.get("unknown_id"),
            "confidence": round(float(event_payload.get("confidence", event_payload.get("confidence_score", 0.75))), 4),
            "snapshot_url": event_payload.get("snapshot_url"),
            "blockchain_verified": event_payload.get("blockchain_verified", True),
            "blockchain_hash": event_payload.get("blockchain_hash", "none"),
            "timestamp": event_payload.get("timestamp", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())),
            "metadata": event_payload.get("metadata") or {
                "sector": event_payload.get("sector"),
                "threat": event_payload.get("threat"),
                "lat": event_payload.get("lat"),
                "lng": event_payload.get("lng")
            }
        }

        # Sub-ms handoff to the bridge thread
        self._loop.call_soon_threadsafe(self._queue.put_nowait, payload)

    async def _connection_manager(self):
        """Maintains the external WebSocket connection with exponential backoff."""
        import ssl
        from urllib.parse import urlparse
        
        while self._is_running:
            try:
                if not websockets:
                    logger.warning("websockets library not installed — using HTTP fallback only")
                    await asyncio.sleep(30)
                    continue
                
                # Extract hostname for SNI
                parsed_url = urlparse(self.ws_url)
                hostname = parsed_url.hostname
                
                # Create a more permissive SSL context to handle SNI/Handshake issues
                ssl_context = ssl.create_default_context()
                ssl_context.check_hostname = False
                ssl_context.verify_mode = ssl.CERT_NONE
                
                logger.info(f"Sentinel Bridge attempting connection to {self.ws_url}...")
                
                # [TLSV1_UNRECOGNIZED_NAME] FIX: 
                # Try connecting WITH explicit hostname first, then WITHOUT if it fails
                try:
                    async with websockets.connect(
                        self.ws_url,
                        ssl=ssl_context,
                        server_hostname=hostname, # Explicit SNI
                        ping_interval=20,
                        ping_timeout=10,
                        close_timeout=5
                    ) as ws:
                        await self._handle_ws_session(ws)
                except Exception as e:
                    if "UNRECOGNIZED_NAME" in str(e) or "handshake" in str(e).lower():
                        logger.warning(f"SNI handshake failed with {hostname}, retrying without SNI...")
                        # Retry without explicit server_hostname (disables SNI)
                        async with websockets.connect(
                            self.ws_url,
                            ssl=ssl_context,
                            server_hostname=None, 
                            ping_interval=20,
                            ping_timeout=10,
                            close_timeout=5
                        ) as ws:
                            await self._handle_ws_session(ws)
                    else:
                        raise e
                    
            except Exception as e:
                self._ws = None
                self._ws_connected = False
                self._last_error = str(e)
                logger.warning(f"Sentinel WS connection failed: {e} — retrying in {self._reconnect_delay:.0f}s (HTTP fallback active)")
                await asyncio.sleep(self._reconnect_delay)
                # Exponential backoff: 1s → 2s → 4s → 8s → max 15s
                self._reconnect_delay = min(self._reconnect_delay * 2, 15.0)

    async def _handle_ws_session(self, ws):
        """Helper to manage an active WS session."""
        self._ws = ws
        self._ws_connected = True
        self._reconnect_delay = 1.0  # Reset backoff on success
        self._last_connected_at = datetime.utcnow().isoformat() + "Z"
        logger.info(f"✅ Sentinel Bridge CONNECTED to {self.ws_url} — real-time mobile alerts ACTIVE")
        
        # Push failed events immediately upon reconnection
        recovered = 0
        while self._failed_queue:
            item = self._failed_queue.popleft()
            if not await self._transmit_ws(item):
                self._failed_queue.appendleft(item)
                break
            recovered += 1
        if recovered:
            logger.info(f"Sentinel Bridge recovered {recovered} queued events")
        
        await ws.wait_closed()
        self._ws_connected = False
        self._ws = None
        logger.info("Sentinel Bridge WebSocket closed — reconnecting...")

    async def _worker(self):
        """High-speed worker that transmits events as they arrive."""
        while self._is_running:
            payload = await self._queue.get()
            
            # Try WebSocket first (primary path)
            if await self._transmit_ws(payload):
                self._total_sent += 1
            # Fallback to HTTP POST if WS unavailable
            elif await self._transmit_http(payload):
                self._total_sent += 1
                self._total_http_fallback += 1
            else:
                # Both failed — queue for retry on reconnection
                self._failed_queue.append(payload)
                self._total_failed += 1
                logger.debug(f"Alert queued for retry ({len(self._failed_queue)} pending)")
            
            self._queue.task_done()

    async def _keepalive_pinger(self):
        """Sends a lightweight heartbeat every 25s to prevent idle WS timeout."""
        while self._is_running:
            await asyncio.sleep(25)
            if self._ws and self._ws_connected:
                try:
                    ping_payload = json.dumps({
                        "type": "heartbeat",
                        "source": "BSC-DOP",
                        "ts": datetime.utcnow().isoformat() + "Z"
                    })
                    await self._ws.send(ping_payload)
                    logger.debug("Sentinel Bridge: keepalive ping sent")
                except Exception as e:
                    logger.debug(f"Sentinel Bridge ping failed: {e}")

    async def _transmit_ws(self, payload: Dict) -> bool:
        """Attempt WebSocket transmission."""
        if not self._ws or self._ws.closed:
            return False
        try:
            await self._ws.send(json.dumps(payload))
            self._last_sent_at = datetime.utcnow().isoformat() + "Z"
            return True
        except Exception:
            self._ws_connected = False
            return False
    
    async def _transmit_http(self, payload: Dict) -> bool:
        """HTTP POST fallback when WebSocket is unavailable."""
        if not self._http_client:
            return False
        try:
            response = await self._http_client.post(
                self.http_url,
                json=payload,
                headers={"Content-Type": "application/json", "X-Source": "BSC-DOP-Sentinel"}
            )
            if response.status_code in (200, 201, 202):
                return True
            else:
                logger.debug(f"HTTP fallback response: {response.status_code}")
                return False
        except Exception as e:
            # HTTP also failed — both channels down
            logger.debug(f"HTTP fallback failed: {e}")
            return False
    
    def get_status(self) -> Dict[str, Any]:
        """Get bridge connection status and metrics."""
        return {
            "ws_connected": self._ws_connected,
            "ws_url": self.ws_url,
            "http_fallback_url": self.http_url,
            "total_sent": self._total_sent,
            "total_failed": self._total_failed,
            "total_http_fallback": self._total_http_fallback,
            "pending_retry": len(self._failed_queue),
            "connection_attempts": self._connection_attempts,
            "last_connected_at": self._last_connected_at,
            "last_sent_at": self._last_sent_at,
            "last_error": self._last_error,
            "running": self._is_running,
        }

# Global instance
from config import settings
sentinel_bridge = SentinelBridge(ws_url=settings.SENTINEL_WS_URL)
