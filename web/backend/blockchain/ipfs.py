import os
import hashlib
import logging
import asyncio
from typing import Optional
from security.manager import encryption_manager

logger = logging.getLogger("bsc.ipfs")

class PrivateIPFSService:
    """
    Simulates a Private IPFS Node for encrypted evidence storage.
    Handles data content-addressing (CID generation) and local persistence.
    """
    
    def __init__(self):
        self.storage_dir = "backend/scratch/ipfs_mock_node"
        os.makedirs(self.storage_dir, exist_ok=True)
        
    def generate_cid(self, data: bytes) -> str:
        """Generates a Content Identifier (CID) based on SHA-256."""
        return "Qm" + hashlib.sha256(data).hexdigest()[:44]

    async def store_evidence(self, raw_frame_bytes: bytes, event_id: str) -> Optional[str]:
        """
        1. Encrypts the raw evidence.
        2. Generates a CID.
        3. Persists to the private IPFS cluster.
        4. Returns the CID for blockchain logging.
        """
        try:
            # Encrypt evidence before storage (Zero Trust)
            encrypted_data = encryption_manager.encrypt_frame(raw_frame_bytes)
            
            # Generate the CID (Content Address)
            cid = self.generate_cid(encrypted_data)
            
            # Persist to local "IPFS Node" storage
            file_path = os.path.join(self.storage_dir, f"{cid}.evidence")
            with open(file_path, "wb") as f:
                f.write(encrypted_data)
                
            logger.info(f"EVIDENCE SECURED | Event: {event_id} | CID: {cid}")
            return cid
            
        except Exception as e:
            logger.error(f"IPFS Storage Failure: {e}")
            return None

    def retrieve_evidence(self, cid: str) -> Optional[bytes]:
        """ Retrieves and decrypts evidence from the IPFS node. """
        try:
            file_path = os.path.join(self.storage_dir, f"{cid}.evidence")
            if not os.path.exists(file_path):
                return None
                
            with open(file_path, "rb") as f:
                encrypted_data = f.read()
                
            # Decrypt with integrity check
            return encryption_manager.decrypt_frame(encrypted_data)
        except Exception as e:
            logger.error(f"Evidence retrieval/decryption failed for {cid}: {e}")
            return None

# Singleton instance
ipfs_service = PrivateIPFSService()
