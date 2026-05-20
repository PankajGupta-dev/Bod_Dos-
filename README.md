# 🚁 AI-Based Border Surveillance Using Drones

> An autonomous, edge-AI powered aerial surveillance system that eliminates border blind spots, detects threats in real time, and logs critical event data on a tamper-proof blockchain ledger.

**Team:** Circuit Forge | **College:** Kalyani Government Engineering College | **Event:** Innovexa 2026
**Track:** Drone & Smart Surveillance Systems | **Domain:** Defense, Border Security & Restricted Zone Monitoring

---

## 📋 Table of Contents

- [The Problem](#the-problem)
- [Our Solution](#our-solution)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [System Architecture](#system-architecture)
- [How It Works](#how-it-works)
- [Compliance & Legal Requirements](#compliance--legal-requirements)
- [Estimated Cost](#estimated-cost)
- [Team](#team)

---

## The Problem

Traditional border infrastructure has critical gaps:

- **Static cameras** create massive blind spots with no dynamic coverage
- **Manual foot patrols** put human personnel at high risk in volatile zones
- Systems **fail in harsh environmental conditions** (night, fog, extreme weather)
- No **real-time automated threat identification** — human operators must monitor feeds constantly
- **No tamper-proof audit trail** for security events

---

## Our Solution

Inspired by the autonomous scout philosophy (similar to the stealth "Garud" drone concept from the film *URI*), this project delivers an **intelligent aerial watchdog**.

Instead of simple remote control, the drone uses onboard sensors and computer vision to **automatically detect, geo-tag, and alert command hubs** about border anomalies within **1–2 seconds** — without any human in the loop.

The drone is built with a **matte black finish** for visual stealth during night and twilight missions. Unlike standard army-green which silhouettes sharply against daytime sky backdrops, the low-reflectivity black profile minimizes detection risk.

---

## Key Features

| Feature | Description |
|---|---|
| 🎯 **Multi-Threat AI Detection** | Identifies unauthorized human crossers, suspicious vehicles, and fire/smoke outbreaks in real time |
| ⚡ **Low-Latency Edge Processing** | Video analytics run directly on the drone's onboard hardware — no cloud round-trip needed |
| 🔗 **Blockchain Event Logging** | Threat hashes, GPS coordinates, and timestamps are irreversibly committed to a local blockchain — tamper-proof by design |
| 📡 **Real-Time Command Dashboard** | Web portal with live telemetry, event logs, and automated emergency action paths |
| 📱 **Mobile Alerts** | Instant push notifications to a Flutter mobile app when threats are detected |
| 🌙 **Night Operations Ready** | Matte black stealth finish optimized for low-visibility missions |

---

## Tech Stack

### Hardware

| Component | Choice |
|---|---|
| Camera / Microcontroller | ESP32-CAM (prototype) |
| Edge AI Processor (production) | NVIDIA Jetson Nano / Raspberry Pi |
| Sensors | Temperature, Smoke, Gas modules |
| Communication | MQTT Protocol over Wi-Fi |

### AI & Computer Vision

- **Language:** Python
- **Libraries:** OpenCV, NumPy
- **Object Detection Model:** YOLOv5 / YOLOv8

### Software & Backend

| Layer | Technologies |
|---|---|
| Web Frontend | React.js, Tailwind CSS, Redux, Socket.IO |
| Mobile App | Flutter & Dart |
| Backend API | Node.js, Express.js, JWT Auth |
| Database | MongoDB |
| Blockchain | Solidity Smart Contracts, Ethereum, Ganache |
| Cloud & Deployment | AWS (EC2, S3), Docker, Nginx |

---

## System Architecture

```
[ Drone: Sensors + Camera ]
          │
          ▼  (MQTT / Wi-Fi)
[ Secured Data Transmission ]
          │
          ▼
[ Edge Processor: YOLOv8 + OpenCV ]
          │
     ┌────┴────┐
     ▼         ▼
 Threat?    No Threat
     │           │
     │           └──► Continue Monitoring
     │
     ├──► [ Web Dashboard + Mobile Alerts ]
     │
     └──► [ Blockchain Ledger (Immutable Log) ]
```

---

## How It Works

1. **Collect** — Onboard sensors track environmental factors (temperature, smoke, gas) while the camera records live video.

2. **Analyze** — Video frames are processed locally on the edge processor. YOLOv8 scans each frame for fire, smoke, vehicles, or humans.

3. **Alert** — If a detected object crosses the confidence threshold, an encrypted alert is pushed instantly to the React web dashboard and Flutter mobile app.

4. **Log** — The event's threat signature hash, GPS coordinates, and timestamp are written to the local blockchain ledger — permanently and tamper-proof.

---

## Compliance & Legal Requirements

Deploying tactical defense drones inside national borders requires clearances across multiple Indian authorities:

| Authority | Requirement |
|---|---|
| **DGCA** | Drone registration for a Unique Identification Number (UIN); NPNT-compliant firmware |
| **Ministry of Home Affairs (MHA)** | Operational certification for deployment near sensitive borders |
| **Ministry of Defence** | Clearance for use along territorial boundaries |
| **DRDO Test Facilities** | Field evaluation to verify resistance to signal jammers and electronic warfare |

---

## Estimated Cost

| Stage | Estimated Cost |
|---|---|
| Lab Prototype | Minimal (ESP32-CAM + off-the-shelf dev boards) |
| Production Unit | ₹1.5L – ₹2.5L (~$1,800 – $3,000 USD) |

The production price includes carbon-fibre ruggedization, thermal/night-vision sensor upgrades, and long-range communication relays — remaining competitive against foreign imports.

---

## Team

**Circuit Forge** — Kalyani Government Engineering College

| Name |
|---|
| Archita Chakraborty |
| Ayan Pal |
| Govind Raj Gupta |
| Pankaj Kumar Gupta |
| Rupanjan Saha |
| Srijita Misra |

---

*Developed for Innovexa 2026 — Powered by SurTech & JIS Group.*
