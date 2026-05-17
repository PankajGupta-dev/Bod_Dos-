import hashlib
import json
import time
import logging
import asyncio
from datetime import datetime
from typing import Dict, Any, List, Optional
from blockchain.contracts import surveillance_contract

logger = logging.getLogger("bsc.blockchain")

class BlockchainLedger:
    """
    Interface for Hyperledger Fabric Detection Logging.
    Handles event hashing and asynchronous submission to the ledger.
    """
    
    def __init__(self):
        self.network_name = "BorderGuardNet"
        self.channel_name = "surveillance-channel"
        self.chaincode_id = "detection-contract"
        self._is_active = True
        self._event_queue = asyncio.Queue()
        self._worker_task = None
        
    def start(self):
        """Start the background blockchain submission worker."""
        if self._worker_task is None:
            # Initialize the Smart Contract with existing cameras
            self._bootstrap_ledger()
            self._worker_task = asyncio.create_task(self._process_queue())
            logger.info("Blockchain Ledger Worker started (Smart Contract Mode)")

    def _bootstrap_ledger(self):
        """Pre-registers the system's tactical camera nodes on the blockchain."""
        try:
            cameras = ["CAM-01", "CAM-02", "CAM-03", "CAM-04", "CAM-SECURE"]
            for cam in cameras:
                surveillance_contract.register_camera(
                    camera_id=cam,
                    owner="GOVIND_HQ",
                    metadata={"type": "tactical_node", "sector": "BORDER_NORTH"}
                )
            logger.info(f"Blockchain Ledger bootstrapped with {len(cameras)} verified nodes.")
        except Exception as e:
            logger.error(f"Ledger bootstrap failed: {e}")

    async def stop(self):
        """Stop the ledger worker."""
        self._is_active = False
        if self._worker_task:
            self._worker_task.cancel()
            try:
                await self._worker_task
            except asyncio.CancelledError:
                pass
            self._worker_task = None

    def generate_event_hash(self, event_data: Dict[str, Any]) -> str:
        """
        Generates a deterministic SHA-256 hash of the detection metadata.
        This hash serves as the 'Fingerprint' on the blockchain.
        """
        # Ensure deterministic JSON representation
        serialized = json.dumps(event_data, sort_keys=True)
        return hashlib.sha256(serialized.encode()).hexdigest()

    async def log_detection(self, 
                            camera_id: str, 
                            detections: List[str], 
                            confidence: float, 
                            alert_level: str,
                            person_name: str = "Unknown",
                            unknown_id: Optional[str] = None,
                            metadata: Optional[Dict] = None,
                            snapshot_cid: Optional[str] = None):
        """
        Queue a detection event for blockchain commitment.
        """
        event = {
            "event_id": f"EVT-{int(time.time()*1000)}",
            "camera_id": camera_id,
            "timestamp": datetime.now().isoformat(),
            "alert_type": alert_level,
            "person_name": person_name,
            "unknown_id": unknown_id,
            "confidence_score": round(confidence, 4),
            "snapshot_hash": snapshot_cid, # Snapshot CID is used as content hash
            "metadata": metadata or {}
        }
        
        # Add the unique cryptographic hash (Fingerprint)
        event["blockchain_hash"] = self.generate_event_hash(event)
        
        # Add to async queue for non-blocking processing
        await self._event_queue.put(event)
        logger.debug(f"Event {event['event_id']} queued for verification.")

    async def _process_queue(self):
        """
        Background worker that pushes events to Hyperledger Fabric.
        """
        while self._is_active:
            try:
                event = await self._event_queue.get()
                await self._simulate_consensus(event)
                self._event_queue.task_done()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Blockchain processing error: {e}")
                await asyncio.sleep(2)

    async def _simulate_consensus(self, event: Dict):
        """Executes Smart Contract logic with aggressive performance optimization."""
        # Reduced consensus delay for high-speed simulation
        await asyncio.sleep(0.05)
        
        try:
            # Execute the Smart Contract logic (Validation + Commitment)
            surveillance_contract.add_detection_event(event)
            
            # Simulated transaction ID (Commitment Hash)
            tx_id = hashlib.sha1(event["blockchain_hash"].encode()).hexdigest()[:16]
            
            # --- AGGRESSIVE EXTERNAL DISPATCH ---
            from bridges.sentinel import sentinel_bridge
            
            verified_payload = {
                **event,
                "blockchain_verified": True,
                "snapshot_url": event.get("snapshot_hash"),
                "verification_tx": tx_id
            }
            
            # Sub-millisecond handoff to the dedicated bridge thread
            sentinel_bridge.emit_alert(verified_payload)
            
            logger.debug(f"BLOCKCHAIN VERIFIED | ID: {event['event_id']} | TX: {tx_id}")
            
        except Exception as e:
            logger.error(f"VERIFICATION FAILURE: {e}")

# Singleton instance
blockchain_ledger = BlockchainLedger()
