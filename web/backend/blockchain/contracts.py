import hashlib
import json
import logging
from datetime import datetime
from typing import Dict, Any, List, Optional

logger = logging.getLogger("bsc.contracts")

class SurveillanceChaincode:
    """
    Simulates Hyperledger Fabric Chaincode (Smart Contract) logic.
    Defines the rules for asset management and ledger interaction.
    """
    
    def __init__(self):
        # Simulated World State (K-V Store)
        self._world_state: Dict[str, Any] = {}
        # Simulated Transaction History
        self._history: List[Dict] = []
        
    def init_ledger(self):
        """Initializes the ledger with basic configuration."""
        logger.info("Chaincode [SurveillanceContract] initialized on channel [surveillance-channel]")

    def register_camera(self, camera_id: str, owner: str, metadata: Dict):
        """Registers a new authorized camera node on the blockchain."""
        asset_key = f"CAM_{camera_id}"
        if asset_key in self._world_state:
            raise ValueError(f"Camera {camera_id} is already registered.")
            
        camera_asset = {
            "asset_type": "camera",
            "id": camera_id,
            "owner": owner,
            "metadata": metadata,
            "registered_at": datetime.now().isoformat(),
            "status": "active"
        }
        self._world_state[asset_key] = camera_asset
        self._record_tx("register_camera", camera_asset)
        return True

    def add_detection_event(self, event_data: Dict[str, Any]):
        """
        Commits a tactical detection event to the ledger.
        Enforces that the camera must be registered before logging.
        """
        camera_id = event_data.get("camera_id")
        if f"CAM_{camera_id}" not in self._world_state:
            logger.warning(f"UNAUTHORIZED ACCESS: Camera {camera_id} attempted to log without registration!")
            raise PermissionError(f"Camera {camera_id} is not a verified node.")
            
        event_id = event_data.get("event_id")
        if event_id in self._world_state:
            raise ValueError("Event ID collision detected. Immutability violation.")
            
        # The core blockchain asset
        event_asset = {
            "asset_type": "event",
            "data": event_data,
            "committed_at": datetime.now().isoformat()
        }
        
        self._world_state[event_id] = event_asset
        self._record_tx("add_detection_event", event_asset)
        return True

    def verify_evidence(self, event_id: str, provided_hash: str) -> bool:
        """
        Verification logic to detect tampering.
        Compares a provided hash (from the evidence) with the one on the ledger.
        """
        asset = self._world_state.get(event_id)
        if not asset:
            return False
            
        stored_hash = asset["data"].get("event_hash")
        return stored_hash == provided_hash

    def fetch_alert_history(self, camera_id: Optional[str] = None) -> List[Dict]:
        """Queries the ledger for event history."""
        results = []
        for key, asset in self._world_state.items():
            if asset.get("asset_type") == "event":
                if not camera_id or asset["data"]["camera_id"] == camera_id:
                    results.append(asset["data"])
        return sorted(results, key=lambda x: x["timestamp"], reverse=True)

    def _record_tx(self, fn: str, payload: Dict):
        """Simulates recording the transaction in the block history."""
        tx = {
            "tx_id": hashlib.sha256(str(datetime.now().timestamp()).encode()).hexdigest()[:16],
            "function": fn,
            "timestamp": datetime.now().isoformat(),
            "payload_hash": hashlib.sha256(json.dumps(payload).encode()).hexdigest()
        }
        self._history.append(tx)
        logger.debug(f"BLOCKCHAIN TX | {fn} | ID: {tx['tx_id']}")

# Singleton instance
surveillance_contract = SurveillanceChaincode()
