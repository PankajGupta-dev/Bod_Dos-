from fastapi import APIRouter, Request, HTTPException, Depends
from typing import Dict
import logging
import hashlib

from ai.engine import detection_engine
from dependencies import get_current_operator
from database import Operator

router = APIRouter(tags=["Security"])
logger = logging.getLogger("bsc.security_api")

@router.post("/push-frame/{camera_id}")
async def push_secure_frame(camera_id: str, request: Request):
    """
    Receives an AES-256-GCM encrypted frame from an authorized drone or camera.
    The body must be binary data: [Nonce] + [Tag] + [Ciphertext].
    """
    try:
        # Get binary payload
        encrypted_data = await request.body()
        
        if not encrypted_data:
            raise HTTPException(status_code=400, detail="Empty frame data")
            
        # Find the camera in the pipeline
        pipeline = detection_engine.pipeline
        capture = pipeline.get_capture(camera_id)
        
        if not capture:
            raise HTTPException(status_code=404, detail=f"Camera {camera_id} not registered")
            
        if not capture.is_encrypted:
            raise HTTPException(status_code=403, detail="Camera is not configured for secure push")
            
        # Push to pipeline (Decryption happens inside)
        capture.push_secure_frame(encrypted_data)
        
        return {"status": "ok", "timestamp": detection_engine.get_status().get("uptime_seconds", 0)}
        
    except ValueError as ve:
        # This occurs if the AES-GCM Tag check fails (Tampering!)
        logger.error(f"SECURITY ALERT: Tamper detection triggered for camera {camera_id}!")
        raise HTTPException(status_code=401, detail="Integrity check failed: Data may have been tampered with.")
@router.get("/verify/{event_id}")
async def verify_blockchain_event(event_id: str, operator: Operator = Depends(get_current_operator)):
    """
    Verifies a detection event against the immutable blockchain ledger.
    Provides proof of authenticity for forensic evidence.
    """
    from blockchain.contracts import surveillance_contract
    from blockchain.ipfs import ipfs_service
    
    # Fetch from ledger
    asset = surveillance_contract._world_state.get(event_id)
    if not asset:
        raise HTTPException(status_code=404, detail="Event not found on blockchain")
        
    event_data = asset["data"]
    
    # Verification check
    is_authentic = surveillance_contract.verify_evidence(event_id, event_data.get("event_hash"))
    
    return {
        "event_id": event_id,
        "is_authentic": is_authentic,
        "timestamp": event_data["timestamp"],
        "blockchain_tx": hashlib.sha1(event_data["event_hash"].encode()).hexdigest()[:16],
        "snapshot_verified": event_data.get("snapshot_cid") is not None,
        "cid": event_data.get("snapshot_cid")
    }
