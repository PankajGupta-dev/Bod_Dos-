"""
System health router
  GET /api/health           — public liveness check
  GET /api/status           — authenticated full system status
  GET /api/sentinel/status  — Sentinel Bridge mobile-link diagnostics
"""
import time
from fastapi import APIRouter, Depends
from sqlmodel import Session, select, func

from database import get_session, Alert, SystemSettings
from dependencies import get_current_operator
from schemas import SystemHealth
from bridges.sentinel import sentinel_bridge

router = APIRouter(tags=["health"])

_START_TIME = time.time()
_VERSION = "v2.4.1-ALPHA"


@router.get("/health")
def health_check():
    """Public — used by Docker/load balancer probes."""
    return {"status": "ok", "version": _VERSION}


@router.get("/status", response_model=SystemHealth)
def system_status(
    session: Session = Depends(get_session),
    _op=Depends(get_current_operator),
):
    from ai.engine import detection_engine
    import random
    
    # Dynamic telemetry based on active tactical ingestion
    # Only count cameras that are SUCCESSFULLY connected
    all_statuses = detection_engine.pipeline.get_all_statuses()
    active_cams = sum(1 for s in all_statuses.values() if s.get("connected"))
    
    active_alerts = session.exec(
        select(func.count(Alert.id)).where(Alert.acknowledged == False)  # noqa: E712
    ).one()

    # SAT LINK: Active if any tactical feed is SUCCESSFULLY streaming
    sat_status = "ONLINE" if active_cams > 0 else "OFFLINE"
    
    # PWR LEVEL: Escalates during active surveillance
    pwr_level = random.randint(95, 98) if active_cams > 0 else 85

    return SystemHealth(
        status="operational",
        drones_online=active_cams,
        sat_link=sat_status,
        power_level=pwr_level,
        uptime_seconds=int(time.time() - _START_TIME),
        active_alerts=active_alerts,
        version=_VERSION,
    )


@router.get("/sentinel/status")
def sentinel_status(_op=Depends(get_current_operator)):
    """
    Diagnostic endpoint for the Sentinel Bridge mobile link.
    Returns real-time connection state, throughput, and last error.
    """
    status = sentinel_bridge.get_status()
    status["connection_label"] = "🟢 LIVE" if status["ws_connected"] else "🔴 RECONNECTING"
    return status
