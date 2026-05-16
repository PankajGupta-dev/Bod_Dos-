"""
BSC-DOP Border Surveillance Command — FastAPI Backend
Entry point: main.py
Run with:  uvicorn main:app --reload --port 8000
"""
import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from database import create_db_and_tables, seed_operators, seed_settings
from routers import auth, alerts, drones, settings as settings_router, health, cameras, security
from routers import detection as detection_router

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
    datefmt="%H:%M:%S",
)


# ─── Lifespan ─────────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    # ── Startup ──
    print("BSC-DOP Backend starting up...")
    create_db_and_tables()
    seed_operators()
    seed_settings()
    print("Database ready.")

    # Start drone telemetry background broadcast loop
    task = asyncio.create_task(drones.telemetry_broadcast_loop())
    print(f"Drone telemetry broadcast started (interval={settings.DRONE_TELEMETRY_INTERVAL}s).")

    # ── Start AI Detection Engine ──
    from ai.engine import detection_engine
    from routers.alerts import _broadcast as alert_broadcast
    from database import Alert, engine as db_engine
    from sqlmodel import Session
    from datetime import datetime
    
    async def on_ai_alert(alert_event):
        """Bridge AI alerts into the existing SSE alert system."""
        try:
            # Save to DB
            with Session(db_engine) as session:
                # We map AI AlertEvent to the persistent DB Alert model
                db_alert = Alert(
                    timestamp=datetime.fromtimestamp(alert_event.timestamp),
                    sector=alert_event.sector,
                    threat=alert_event.threat,
                    camera=alert_event.camera_id,
                    lat=32.4482, # Simulated tactical coordinates
                    lng=74.3411, 
                    alert_type=alert_event.alert_type
                )
                session.add(db_alert)
                session.commit()
                session.refresh(db_alert)
                
                # Enrich with AI descriptions for the UI
                alert_dict = db_alert.model_dump()
                alert_dict["id"] = db_alert.id
                alert_dict["ai_description"] = alert_event.description
                alert_dict["ai_severity"] = alert_event.severity
                alert_dict["ai_detections"] = alert_event.detections
                
                # Broadcast via SSE (Live Dashboard)
                await alert_broadcast(alert_dict)
                logger.info(f"[{alert_event.camera_id}] AI ALERT DISPATCHED: {alert_event.threat}")
        except Exception as e:
            logger.error(f"Error bridging AI alert: {e}")

    # Register callback
    detection_engine.set_alert_callback(on_ai_alert, asyncio.get_event_loop())
    detection_engine.start()

    yield

    # ── Shutdown ──
    print("Shutting down...")
    task.cancel()
    detection_engine.stop()


# ─── App Instance ─────────────────────────────────────────────────────────────

app = FastAPI(
    title="BSC-DOP Command Center",
    description="Strategic Border Surveillance & Tactical Intelligence Platform",
    version="2.5.0",
    lifespan=lifespan,
    docs_url="/api/docs",
    redoc_url="/api/redoc",
    openapi_url="/api/openapi.json",
)

# ─── CORS ─────────────────────────────────────────────────────────────────────

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─── Routers ──────────────────────────────────────────────────────────────────

app.include_router(auth.router, prefix="/api/auth")
app.include_router(alerts.router, prefix="/api/alerts")
app.include_router(drones.router, prefix="/api/drones")
app.include_router(detection_router.router, prefix="/api/detection")
app.include_router(settings_router.router, prefix="/api/settings")
app.include_router(security.router, prefix="/api")
app.include_router(health.router, prefix="/api")
app.include_router(cameras.router, prefix="/api/cameras")

@app.get("/")
async def root():
    return {
        "status": "online",
        "system": "BSC-DOP Border Surveillance Command",
        "version": "2.5.0",
        "security_protocol": "AES-256-GCM"
    }
