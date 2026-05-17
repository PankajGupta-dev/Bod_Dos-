"""
Detection Engine — Main orchestrator for the AI detection pipeline.
Runs all detectors on all cameras in a background thread, produces
annotated frames and detection events.
"""
import asyncio
import cv2
import json
import logging
import os
import threading
import time
from collections import defaultdict
from dataclasses import dataclass
from typing import Callable, Dict, List, Optional

import numpy as np

from ai.config import ai_config
from ai.video_pipeline import VideoPipeline
from ai.detector_yolo import YOLODetector, Detection
from ai.detector_fire import FireDetector
from ai.detector_weapon import WeaponDetector
from ai.tracker import ObjectTracker
from ai.alert_fusion import AlertFusionEngine, AlertEvent
from blockchain.ledger import blockchain_ledger
from blockchain.ipfs import ipfs_service
from bridges.sentinel import sentinel_bridge

logger = logging.getLogger("bsc.engine")

# Bounding box colors (BGR)
COLORS = {
    "person":  (0, 255, 0),      # Green
    "vehicle": (255, 153, 0),    # Blue-orange
    "fire":    (0, 0, 255),      # Red
    "weapon":  (0, 0, 255),      # Red
    "bicycle": (255, 153, 0),
    "car":     (255, 153, 0),
    "motorcycle": (255, 153, 0),
    "bus":     (255, 153, 0),
    "truck":   (255, 153, 0),
    "knife":   (0, 0, 255),
}


@dataclass
class CameraDetectionState:
    """Per-camera detection state."""
    camera_id: str
    tracker: ObjectTracker
    frame_count: int = 0
    last_detections: list = None
    last_fire: list = None
    last_weapons: list = None
    annotated_frame: Optional[np.ndarray] = None
    detection_fps: float = 0.0
    humans: int = 0
    vehicles: int = 0
    fire: bool = False
    weapons: int = 0
    moving_objects: list = None

    def __post_init__(self):
        self.last_detections = []
        self.last_fire = []
        self.last_weapons = []
        self.moving_objects = []


class DetectionEngine:
    """
    Orchestrates the full AI detection pipeline.
    - Captures frames from all cameras via VideoPipeline
    - Runs YOLO, Fire, Weapon detectors
    - Tracks objects, analyzes movement
    - Generates intelligent alerts
    - Produces annotated frames for MJPEG streaming
    """

    def __init__(self):
        self.pipeline = VideoPipeline()
        self.yolo = YOLODetector()
        self.fire_detector = FireDetector()
        self.weapon_detector = WeaponDetector()
        self.alert_engine = AlertFusionEngine()

        self._camera_states: Dict[str, CameraDetectionState] = {}
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()
        self._models_loaded = False

        # Subscribers for detection events (called from detection thread)
        self._event_callbacks: list[Callable] = []

        # Alert callback (async, called via asyncio)
        self._alert_callback: Optional[Callable] = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None

        # Camera → sector mapping
        self._camera_sectors: Dict[str, str] = {}

        # FPS tracking
        self._fps_counters: Dict[str, list] = defaultdict(list)

        # Engine stats
        self.total_detections = 0
        self.total_alerts = 0
        self.start_time = 0.0

        # Per-detection-type cooldowns: "camera_id:type" -> last_notification_time
        self._detection_cooldowns: Dict[str, float] = {}

    def load_models(self) -> bool:
        """Load all AI models. Call once on startup."""
        logger.info("Loading AI detection models...")
        os.makedirs(ai_config.MODELS_DIR, exist_ok=True)

        if not self.yolo.load():
            logger.error("Failed to load YOLO model")
            return False

        self.weapon_detector.load_custom_model()  # Optional
        self._models_loaded = True
        logger.info(
            f"AI models loaded — Device: {self.yolo.device_info}, "
            f"YOLO: {'✓' if self.yolo.is_loaded else '✗'}"
        )
        return True

    def start(self):
        """Start the detection engine background thread."""
        if self._running:
            return
        if not self._models_loaded:
            if not self.load_models():
                logger.error("Cannot start engine — models not loaded")
                return

        self._running = True
        self.start_time = time.time()
        self._thread = threading.Thread(
            target=self._detection_loop,
            name="detection-engine",
            daemon=True,
        )
        self._thread.start()
        
        # Start Blockchain Ledger Worker
        blockchain_ledger.start()
        
        logger.info("Detection engine started")

    def stop(self):
        """Stop the detection engine."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=10)
            self._thread = None
        self.pipeline.stop_all()
        
        # Stop Blockchain Ledger Worker
        if self._loop:
            asyncio.run_coroutine_threadsafe(blockchain_ledger.stop(), self._loop)
            
        logger.info("Detection engine stopped")

    def add_camera(
        self, camera_id: str, url: str, sector: str = "UNKNOWN"
    ) -> bool:
        """Register a camera for AI detection."""
        success = self.pipeline.add_camera(camera_id, url)
        if success:
            with self._lock:
                if camera_id not in self._camera_states:
                    self._camera_states[camera_id] = CameraDetectionState(
                        camera_id=camera_id,
                        tracker=ObjectTracker(),
                    )
                self._camera_sectors[camera_id] = sector
        return success

    def remove_camera(self, camera_id: str):
        """Remove a camera from AI detection."""
        self.pipeline.remove_camera(camera_id)
        with self._lock:
            self._camera_states.pop(camera_id, None)
            self._camera_sectors.pop(camera_id, None)

    def get_annotated_frame(self, camera_id: str) -> Optional[np.ndarray]:
        """Get the latest AI-annotated frame for a camera."""
        with self._lock:
            state = self._camera_states.get(camera_id)
            if state and state.annotated_frame is not None:
                return state.annotated_frame.copy()
        return None

    def get_detections(self, camera_id: str) -> dict:
        """Get current detection summary for a camera."""
        with self._lock:
            state = self._camera_states.get(camera_id)
            if not state:
                return {
                    "camera_id": camera_id, "humans": 0, "vehicles": 0,
                    "fire": False, "weapons": 0, "moving_objects": [],
                    "detection_fps": 0.0,
                }
            return {
                "camera_id": camera_id,
                "humans": state.humans,
                "vehicles": state.vehicles,
                "fire": state.fire,
                "weapons": state.weapons,
                "moving_objects": state.moving_objects,
                "detection_fps": round(state.detection_fps, 1),
            }

    def get_all_detections(self) -> dict:
        """Get detection summary for all cameras."""
        with self._lock:
            return {
                cid: {
                    "camera_id": cid,
                    "humans": s.humans,
                    "vehicles": s.vehicles,
                    "fire": s.fire,
                    "weapons": s.weapons,
                    "moving_objects": s.moving_objects,
                    "detection_fps": round(s.detection_fps, 1),
                }
                for cid, s in self._camera_states.items()
            }

    def get_status(self) -> dict:
        """Get engine status."""
        uptime = time.time() - self.start_time if self.start_time else 0
        return {
            "running": self._running,
            "models_loaded": self._models_loaded,
            "device": self.yolo.device_info if self._models_loaded else "none",
            "yolo_inference_ms": (
                round(self.yolo.inference_time_ms, 1)
                if self._models_loaded else 0
            ),
            "cameras": len(self._camera_states),
            "total_detections": self.total_detections,
            "total_alerts": self.total_alerts,
            "uptime_seconds": int(uptime),
            "camera_statuses": self.pipeline.get_all_statuses(),
        }

    def set_alert_callback(self, callback: Callable, loop: asyncio.AbstractEventLoop):
        """Set async callback for alert events."""
        self._alert_callback = callback
        self._loop = loop

    def subscribe_events(self, callback: Callable):
        """Subscribe to detection events."""
        self._event_callbacks.append(callback)
        return lambda: self._event_callbacks.remove(callback)

    # ─── Detection Loop ───────────────────────────────────────────────────────

    def _detection_loop(self):
        """Main detection loop — processes cameras in parallel for low latency."""
        from concurrent.futures import ThreadPoolExecutor
        logger.info("Parallel detection engine started (4-camera optimized)")
        
        # Optimized for Intel Arc multi-stream throughput
        max_workers = ai_config.MAX_CAMERAS 
        
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            while self._running:
                camera_ids = self.pipeline.get_camera_ids()
                if not camera_ids:
                    time.sleep(0.5)
                    continue

                loop_start = time.time()
                
                # Submit all cameras to the pool for parallel processing
                futures = []
                for camera_id in camera_ids:
                    frame = self.pipeline.get_frame(camera_id)
                    if frame is not None:
                        futures.append(executor.submit(self._process_frame, camera_id, frame))
                
                # Wait for this batch to complete to prevent queue buildup
                for future in futures:
                    try:
                        future.result(timeout=1.0)
                    except Exception as e:
                        logger.error(f"Parallel process error: {e}")

                # Maintain target FPS across the system
                elapsed = time.time() - loop_start
                target_interval = 1.0 / ai_config.DETECTION_FPS
                if elapsed < target_interval:
                    time.sleep(target_interval - elapsed)

    def _process_frame(self, camera_id: str, frame: np.ndarray):
        """Process a single frame through all detectors."""
        t0 = time.time()
        with self._lock:
            state = self._camera_states.get(camera_id)
            if not state:
                return

        state.frame_count += 1

        # Skip frames for performance
        if state.frame_count % ai_config.FRAME_SKIP != 0:
            # Still update annotated frame with last known detections
            annotated = self._draw_annotations(
                frame, state.last_detections, state.last_fire,
                state.last_weapons, state
            )
            with self._lock:
                state.annotated_frame = annotated
            return

        # ── 1. YOLO Detection (Human + Vehicle + Knife) ──
        yolo_dets = self.yolo.detect(frame)

        # ── 2. Fire Detection ──
        fire_dets = self.fire_detector.detect(frame, camera_id)

        # ── 3. Weapon Validation ──
        weapon_dets = self.weapon_detector.validate(yolo_dets, frame, camera_id)

        # ── 4. Object Tracking ──
        persons_vehicles = [d for d in yolo_dets if d.category in ("person", "vehicle")]
        tracked = state.tracker.update(persons_vehicles)
        movement = state.tracker.get_movement_summary()

        # ── 5. Count detections ──
        humans = len([d for d in yolo_dets if d.category == "person"])
        vehicles = len([d for d in yolo_dets if d.category == "vehicle"])
        fire = len(fire_dets) > 0
        weapons = len(weapon_dets)

        # ── 6. Update state ──
        with self._lock:
            state.last_detections = yolo_dets
            state.last_fire = fire_dets
            state.last_weapons = weapon_dets
            state.humans = humans
            state.vehicles = vehicles
            state.fire = fire
            state.weapons = weapons
            state.moving_objects = movement.get("moving_objects", [])

        self.total_detections += len(yolo_dets) + len(fire_dets) + len(weapon_dets)

        # ── 7. Draw annotations ──
        annotated = self._draw_annotations(
            frame, yolo_dets, fire_dets, weapon_dets, state
        )
        with self._lock:
            state.annotated_frame = annotated

        # ── 8. Generate alerts ──
        avg_conf = (
            np.mean([d.confidence for d in yolo_dets]) if yolo_dets else 0.5
        )
        sector = self._camera_sectors.get(camera_id, "UNKNOWN")

        alert = self.alert_engine.process(
            camera_id=camera_id,
            humans=humans,
            vehicles=vehicles,
            fire=fire,
            weapons=weapons,
            moving_objects=movement.get("moving_objects", []),
            avg_confidence=avg_conf,
            sector=sector,
        )

        if alert:
            self.total_alerts += 1
            # Pass the raw frame for evidence capture if needed
            self._dispatch_alert(alert, frame)
        
        # ── 9. Real-time notifications → Mobile + Threat Log + Dashboard ──
        if humans > 0:
            self._notify_detection(camera_id, "human", humans, avg_conf, sector)
        if vehicles > 0:
            self._notify_detection(camera_id, "vehicle", vehicles, avg_conf, sector)
        if weapons > 0:
            self._notify_detection(camera_id, "weapon", weapons, avg_conf, sector)
        if fire:
            self._notify_detection(camera_id, "fire", 1, avg_conf, sector)

        # ── 10. Broadcast detection events ──
        self._broadcast_events(camera_id, state)

    def _draw_annotations(
        self,
        frame: np.ndarray,
        yolo_dets: list,
        fire_dets: list,
        weapon_dets: list,
        state: CameraDetectionState,
    ) -> np.ndarray:
        """Draw bounding boxes and labels on the frame."""
        annotated = frame.copy()
        h, w = annotated.shape[:2]

        # Draw YOLO detections (persons + vehicles)
        for det in yolo_dets:
            color = COLORS.get(det.class_name, COLORS.get(det.category, (255, 255, 255)))
            x1, y1, x2, y2 = det.bbox
            cv2.rectangle(annotated, (x1, y1), (x2, y2), color, 2)
            label = f"{det.class_name} {det.confidence:.0%}"
            self._draw_label(annotated, label, (x1, y1), color)

        # Draw fire detections
        for fire in fire_dets:
            x1, y1, x2, y2 = fire.bbox
            cv2.rectangle(annotated, (x1, y1), (x2, y2), (0, 0, 255), 3)
            label = f"FIRE {fire.confidence:.0%}"
            self._draw_label(annotated, label, (x1, y1), (0, 0, 255))
            # Pulsing overlay
            overlay = annotated.copy()
            cv2.rectangle(overlay, (x1, y1), (x2, y2), (0, 0, 255), -1)
            cv2.addWeighted(overlay, 0.15, annotated, 0.85, 0, annotated)

        # Draw weapon detections
        for wpn in weapon_dets:
            x1, y1, x2, y2 = wpn.bbox
            cv2.rectangle(annotated, (x1, y1), (x2, y2), (0, 0, 255), 3)
            label = f"⚠ {wpn.class_name.upper()} {wpn.confidence:.0%}"
            self._draw_label(annotated, label, (x1, y1), (0, 0, 255))

        # Draw tracked movement arrows
        for track in state.tracker.get_all_tracks():
            if track.is_moving and track.missed == 0:
                cx, cy = track.center
                # Draw a small arrow indicating direction
                dx, dy = 0, 0
                if track.direction == "right": dx = 20
                elif track.direction == "left": dx = -20
                elif track.direction == "down": dy = 20
                elif track.direction == "up": dy = -20
                elif track.direction == "approaching": dy = 15
                elif track.direction == "retreating": dy = -15

                if dx != 0 or dy != 0:
                    cv2.arrowedLine(
                        annotated, (cx, cy), (cx + dx, cy + dy),
                        (0, 255, 255), 2, tipLength=0.5
                    )

        # Draw HUD info bar at top
        self._draw_hud(annotated, state)

        return annotated

    def _draw_label(self, frame, text, pos, color):
        """Draw a label with background."""
        x, y = pos
        font = cv2.FONT_HERSHEY_SIMPLEX
        scale = 0.5
        thickness = 1
        (tw, th), baseline = cv2.getTextSize(text, font, scale, thickness)
        y_text = max(y - 5, th + 5)
        cv2.rectangle(
            frame, (x, y_text - th - 5), (x + tw + 6, y_text + 3),
            color, -1
        )
        cv2.putText(
            frame, text, (x + 3, y_text - 2),
            font, scale, (0, 0, 0), thickness, cv2.LINE_AA
        )

    def _draw_hud(self, frame, state: CameraDetectionState):
        """Draw detection summary HUD at top of frame."""
        h, w = frame.shape[:2]
        bar_h = 28

        # Semi-transparent bar
        overlay = frame.copy()
        cv2.rectangle(overlay, (0, 0), (w, bar_h), (0, 0, 0), -1)
        cv2.addWeighted(overlay, 0.7, frame, 0.3, 0, frame)

        font = cv2.FONT_HERSHEY_SIMPLEX
        y = 19
        x = 8

        items = [
            (f"H:{state.humans}", (0, 255, 0) if state.humans > 0 else (100, 100, 100)),
            (f"V:{state.vehicles}", (255, 153, 0) if state.vehicles > 0 else (100, 100, 100)),
            (f"F:{'YES' if state.fire else 'NO'}", (0, 0, 255) if state.fire else (100, 100, 100)),
            (f"W:{state.weapons}", (0, 0, 255) if state.weapons > 0 else (100, 100, 100)),
            (f"FPS:{state.detection_fps:.0f}", (0, 255, 0)),
        ]

        for text, color in items:
            cv2.putText(frame, text, (x, y), font, 0.45, color, 1, cv2.LINE_AA)
            (tw, _), _ = cv2.getTextSize(text, font, 0.45, 1)
            x += tw + 15

        # AI badge
        cv2.putText(
            frame, "AI", (w - 35, y), font, 0.5, (0, 255, 0), 2, cv2.LINE_AA
        )

    def _update_fps(self, camera_id: str, elapsed: float):
        """Update detection FPS tracking."""
        now = time.time()
        self._fps_counters[camera_id].append(now)
        cutoff = now - 2.0
        self._fps_counters[camera_id] = [
            t for t in self._fps_counters[camera_id] if t > cutoff
        ]
        fps = len(self._fps_counters[camera_id]) / 2.0

        with self._lock:
            state = self._camera_states.get(camera_id)
            if state:
                state.detection_fps = fps

    def _dispatch_alert(self, alert: AlertEvent, frame: Optional[np.ndarray] = None):
        """Dispatch alert to the async callback (FastAPI SSE system) and Blockchain."""
        if self._alert_callback and self._loop:
            try:
                # 1. Dispatch to Dashboard (Live UI)
                asyncio.run_coroutine_threadsafe(
                    self._alert_callback(alert), self._loop
                )
                
                # 2. Forensic Evidence Capture (IPFS)
                # Only capture snapshots for significant threats to optimize storage/bandwidth
                snapshot_cid = None
                if alert.severity in ("critical", "emergency", "high") and frame is not None:
                    # Encode to JPEG for storage
                    _, buffer = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 90])
                    frame_bytes = buffer.tobytes()
                    
                    # We wrap the async IPFS call to run in the background
                    async def capture_evidence():
                        cid = await ipfs_service.store_evidence(frame_bytes, alert.camera_id)
                        
                        # 3. Log to Private Blockchain with Evidence Link
                        active_detections = [cat for cat, count in alert.detections.items() if count > 0 or count is True]
                        await blockchain_ledger.log_detection(
                            camera_id=alert.camera_id,
                            detections=active_detections,
                            confidence=alert.confidence,
                            alert_level=alert.severity,
                            person_name=getattr(alert, 'person_name', "Unknown"),
                            unknown_id=getattr(alert, 'unknown_id', None),
                            metadata={
                                "sector": alert.sector,
                                "threat": alert.threat,
                                "description": alert.description
                            },
                            snapshot_cid=cid
                        )
                    
                    asyncio.run_coroutine_threadsafe(capture_evidence(), self._loop)
                else:
                    # Log to Blockchain without snapshot for lower severity
                    active_detections = [cat for cat, count in alert.detections.items() if count > 0 or count is True]
                    async def log_and_notify():
                        await blockchain_ledger.log_detection(
                            camera_id=alert.camera_id,
                            detections=active_detections,
                            confidence=alert.confidence,
                            alert_level=alert.severity,
                            person_name=getattr(alert, 'person_name', "Unknown"),
                            unknown_id=getattr(alert, 'unknown_id', None),
                            metadata={
                                "sector": alert.sector,
                                "threat": alert.threat,
                                "description": alert.description
                            }
                        )
                    asyncio.run_coroutine_threadsafe(log_and_notify(), self._loop)
            except Exception as e:
                logger.error(f"Alert dispatch/blockchain error: {e}")

    def _broadcast_events(self, camera_id: str, state: CameraDetectionState):
        """Broadcast detection events to subscribers."""
        event_data = {
            "camera_id": camera_id,
            "timestamp": time.time(),
            "humans": state.humans,
            "vehicles": state.vehicles,
            "fire": state.fire,
            "weapons": state.weapons,
            "moving_objects": state.moving_objects,
            "detection_fps": round(state.detection_fps, 1),
        }

        for callback in self._event_callbacks:
            try:
                callback(event_data)
            except Exception as e:
                logger.error(f"Event broadcast error: {e}")

    def _notify_detection(self, camera_id: str, detection_type: str, count: int, confidence: float, sector: str = "UNKNOWN"):
        """
        Unified real-time notifier for ALL detection types: human, vehicle, weapon, fire.
        Every detection fires to:
          1. Sentinel Bridge  → Mobile push notification
          2. Alert Callback   → DB (Threat Log) + SSE (Live Dashboard)
        Uses a 5-second per-type cooldown per camera to prevent flooding.
        """
        now = time.time()
        cooldown_key = f"{camera_id}:{detection_type}"
        if (now - self._detection_cooldowns.get(cooldown_key, 0)) < 5.0:
            return
        self._detection_cooldowns[cooldown_key] = now

        # ── Build labels per detection type ──
        if detection_type == "human":
            threat_label  = f"{count} Human{'s' if count > 1 else ''} Detected"
            description   = f"Human presence: {count} individual{'s' if count > 1 else ''} detected in sector {sector}"
            severity      = "high" if count >= 3 else "medium"
            alert_type    = "critical" if count >= 3 else "warning"
            mobile_type   = "HUMAN_DETECTED"
            detections    = {"humans": count, "vehicles": 0, "fire": False, "weapons": 0}
        elif detection_type == "vehicle":
            threat_label  = f"{count} Vehicle{'s' if count > 1 else ''} Detected"
            description   = f"Vehicle activity: {count} vehicle{'s' if count > 1 else ''} spotted in sector {sector}"
            severity      = "medium"
            alert_type    = "warning"
            mobile_type   = "VEHICLE_DETECTED"
            detections    = {"humans": 0, "vehicles": count, "fire": False, "weapons": 0}
        elif detection_type == "weapon":
            threat_label  = f"Weapon{'s' if count > 1 else ''} Detected"
            description   = f"DANGER: {count} weapon{'s' if count > 1 else ''} identified in sector {sector}"
            severity      = "critical"
            alert_type    = "critical"
            mobile_type   = "WEAPON_DETECTED"
            detections    = {"humans": 0, "vehicles": 0, "fire": False, "weapons": count}
        elif detection_type == "fire":
            threat_label  = "Fire / Smoke Detected"
            description   = f"Fire or smoke detected in sector {sector} — immediate response required"
            severity      = "high"
            alert_type    = "critical"
            mobile_type   = "FIRE_DETECTED"
            detections    = {"humans": 0, "vehicles": 0, "fire": True, "weapons": 0}
        else:
            return

        # ── 1. Mobile Push via Sentinel Bridge (non-blocking) ──
        sentinel_bridge.emit_alert({
            "event_id":          f"{detection_type.upper()}-{int(now * 1000)}",
            "camera_id":         camera_id,
            "alert_type":        mobile_type,
            "confidence":        round(confidence, 4),
            "timestamp":         time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(now)),
            "blockchain_verified": False,
            "metadata":          {"count": count, "sector": sector, "threat": threat_label},
        })

        # ── 2. Threat Log (DB) + Live Dashboard (SSE) via alert callback ──
        if self._alert_callback and self._loop:
            alert_event = AlertEvent(
                camera_id=camera_id,
                sector=sector,
                threat=threat_label,
                description=description,
                severity=severity,
                alert_type=alert_type,
                confidence=round(confidence, 3),
                detections=detections,
                moving_objects=[],
                timestamp=now,
            )
            try:
                asyncio.run_coroutine_threadsafe(
                    self._alert_callback(alert_event), self._loop
                )
            except Exception as e:
                logger.error(f"[{camera_id}] Notification callback error ({detection_type}): {e}")

        logger.info(f"[{camera_id}] {detection_type.upper()} → Mobile + ThreatLog + SSE: {threat_label}")


# ─── Global singleton ────────────────────────────────────────────────────────
detection_engine = DetectionEngine()
