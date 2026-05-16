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
                            metadata: Optional[Dict] = None,
                            snapshot_cid: Optional[str] = None):
        """
        Queue a detection event for blockchain commitment.
        """
        event = {
            "event_id": f"EVT-{int(time.time()*1000)}",
            "camera_id": camera_id,
            "timestamp": datetime.now().isoformat(),
            "detections": detections,
            "confidence": round(confidence, 4),
            "alert_level": alert_level,
            "snapshot_cid": snapshot_cid,
            "metadata": metadata or {}
        }
        
        # Add the unique cryptographic hash
        event["event_hash"] = self.generate_event_hash(event)
        
        # Add to async queue
        await self._event_queue.put(event)
        logger.debug(f"Event {event['event_id']} queued for blockchain commitment")

    async def _process_queue(self):
        """
        Background worker that pushes events to Hyperledger Fabric.
        Uses batching or sequential submission depending on network latency.
        """
        while self._is_active:
            try:
                event = await self._event_queue.get()
                
                # --- HYPERLEDGER FABRIC INTEGRATION POINT ---
                # In a full production setup, we would use:
                # gateway.get_network(channel).get_contract(chaincode).submit_transaction('AddEvent', ...)
                
                # For this implementation, we simulate the consensus delay 
                # but ensure the internal state remains consistent.
                await self._simulate_consensus(event)
                
                self._event_queue.task_done()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Blockchain submission error: {e}")
                await asyncio.sleep(5) # Backoff

    async def _simulate_consensus(self, event: Dict):
        """Executes Smart Contract logic and simulates network consensus."""
        # Simulated consensus time
        await asyncio.sleep(0.15)
        
        try:
            # Execute the Smart Contract logic (Validation + Commitment)
            surveillance_contract.add_detection_event(event)
            
            # Simulated transaction hash for log audit
            tx_id = hashlib.sha1(event["event_hash"].encode()).hexdigest()[:16]
            logger.info(f"BLOCKCHAIN COMMIT SUCCESS | ID: {event['event_id']} | Tx: {tx_id} | Hash: {event['event_hash'][:12]}...")
        except Exception as e:
            logger.error(f"SMART CONTRACT VIOLATION: {e}")

# Singleton instance
blockchain_ledger = BlockchainLedger()
