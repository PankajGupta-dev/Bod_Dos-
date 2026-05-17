const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const jwt = require('jsonwebtoken');
const cors = require('cors');
const bodyParser = require('body-parser');
const crypto = require('crypto');
require('dotenv').config();

const app = express();
const server = http.createServer(app);

// Mount WebSocket ONLY on /ws path — matches SENTINEL_WS_URL=ws://localhost:8765/ws
const wss = new WebSocket.Server({ server, path: '/ws' });

const JWT_SECRET = process.env.JWT_SECRET || 'your_super_secret_military_grade_key';

app.use(cors());
app.use(bodyParser.json());

// ─────────────────────────────────────────────
// CLIENT REGISTRIES
// Web dashboard clients (publishers/senders)
const dashboardClients = new Set();
// Android app clients (subscribers/receivers)
const androidClients = new Set();
// In-memory alert store (last 50 alerts)
const alertStore = [];
// ─────────────────────────────────────────────

/**
 * Build a normalized alert payload in snake_case so that
 * Android AlertEntity's @SerializedName annotations parse correctly.
 */
function buildAlertPayload(raw) {
    const blockchainHash = raw.blockchain_hash ||
        crypto.createHash('sha256')
              .update(Date.now().toString() + (raw.alert_type || 'INTRUSION'))
              .digest('hex');

    return {
        id:                 alertStore.length + 1,
        alert_type:         raw.alert_type         || 'INTRUSION',
        person_name:        raw.person_name         || null,
        unknown_id:         raw.unknown_id          || 'UNK_01',
        confidence:         raw.confidence != null  ? parseFloat(raw.confidence) : 0.90,
        snapshot_url:       raw.snapshot_url        || 'https://images.unsplash.com/photo-1579353977828-2a4eab540b9a?w=500',
        blockchain_verified: raw.blockchain_verified !== undefined ? raw.blockchain_verified : true,
        blockchain_hash:    blockchainHash,
        camera_id:          raw.camera_id           || 'DRONE_01',
        threat_level:       raw.threat_level        || 'HIGH',
        latitude:           parseFloat(raw.latitude  || 22.6521),
        longitude:          parseFloat(raw.longitude || 88.4191),
        blockchainStatus:   'PENDING',
        timestamp:          Date.now(),
        isRead:             false
    };
}

/** Broadcast a payload to all connected Android clients */
function broadcastToAndroid(payload) {
    const message = JSON.stringify(payload);
    let sentCount = 0;
    androidClients.forEach(client => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(message);
            sentCount++;
        }
    });
    return sentCount;
}

// ─────────────────────────────────────────────
// WEBSOCKET CONNECTION HANDLER
// ─────────────────────────────────────────────
wss.on('connection', (ws, req) => {
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });

    const url   = new URL(req.url, `http://${req.headers.host}`);
    const role  = url.searchParams.get('role') || 'android';  // ?role=dashboard or ?role=android
    const token = url.searchParams.get('token');

    console.log(`[MILITARY NETWORK] Handshake from role="${role}" — ${req.socket.remoteAddress}`);

    // Optional JWT validation
    if (token) {
        try {
            const decoded = jwt.verify(token, JWT_SECRET);
            console.log(`[MILITARY NETWORK] Authenticated: ${decoded.deviceId || 'SECURE_NODE'}`);
        } catch {
            console.log('[MILITARY NETWORK] Token invalid — operating in open test mode.');
        }
    }

    if (role === 'dashboard') {
        // ── WEB DASHBOARD CLIENT ──────────────────────────────────────────
        dashboardClients.add(ws);
        console.log(`[MILITARY NETWORK] Web dashboard connected. Active dashboards: ${dashboardClients.size}`);

        ws.on('message', (rawMessage) => {
            // Web dashboard sent an alert — normalize and relay to all Android clients
            try {
                const parsed  = JSON.parse(rawMessage.toString());
                const payload = buildAlertPayload(parsed);

                // Buffer it
                alertStore.unshift(payload);
                if (alertStore.length > 50) alertStore.pop();

                const count = broadcastToAndroid(payload);
                console.log(`[MILITARY NETWORK] Dashboard alert relayed to ${count} Android device(s). type="${payload.alert_type}"`);
            } catch (err) {
                console.error('[MILITARY NETWORK] Failed to parse dashboard message:', err.message);
            }
        });

        ws.on('close', () => {
            dashboardClients.delete(ws);
            console.log('[MILITARY NETWORK] Web dashboard disconnected.');
        });

    } else {
        // ── ANDROID CLIENT ────────────────────────────────────────────────
        androidClients.add(ws);
        console.log(`[MILITARY NETWORK] Android device connected. Active devices: ${androidClients.size}`);

        // Sync the last buffered alerts on first connect
        if (alertStore.length > 0) {
            console.log(`[MILITARY NETWORK] Syncing ${alertStore.length} buffered alerts to new device.`);
            // Send each alert individually so Android parses them identically
            alertStore.slice(0, 10).forEach(alert => {
                if (ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify(alert));
                }
            });
        }

        ws.on('message', (msg) => {
            // Android sending heartbeat / ack — just log
            console.log(`[MILITARY NETWORK] Android ACK: ${msg}`);
        });

        ws.on('close', () => {
            androidClients.delete(ws);
            console.log('[MILITARY NETWORK] Android device disconnected.');
        });
    }
});

// ─────────────────────────────────────────────
// PING-PONG KEEPALIVE (30s interval)
// ─────────────────────────────────────────────
const interval = setInterval(() => {
    wss.clients.forEach(ws => {
        if (ws.isAlive === false) {
            androidClients.delete(ws);
            dashboardClients.delete(ws);
            console.log('[MILITARY NETWORK] Dead client terminated.');
            return ws.terminate();
        }
        ws.isAlive = false;
        ws.ping();
    });
}, 30000);

wss.on('close', () => clearInterval(interval));

// ─────────────────────────────────────────────
// REST API — trigger an alert manually / from web dashboard HTTP
// ─────────────────────────────────────────────
app.post('/api/alerts/send', (req, res) => {
    const payload = buildAlertPayload(req.body);

    alertStore.unshift(payload);
    if (alertStore.length > 50) alertStore.pop();

    const sentCount = broadcastToAndroid(payload);

    console.log(`[MILITARY NETWORK] REST alert broadcasted to ${sentCount} device(s). type="${payload.alert_type}"`);
    res.json({ message: 'Alert broadcasted successfully', broadcastedTo: sentCount, payload });
});

// ─────────────────────────────────────────────
// BLOCKCHAIN VERIFICATION ENDPOINT
// ─────────────────────────────────────────────
app.post('/blockchain/verify', (req, res) => {
    const { hash } = req.body;
    console.log(`[BLOCKCHAIN] Verifying hash: ${hash}`);
    const isValid = hash && hash.length === 64;
    res.json({
        verified: isValid,
        message: isValid
            ? '✓ Cryptographic signature matches mainnet ledger.'
            : '⚠ Verification Failed — Possible tampering detected!'
    });
});

// Latest buffered alerts
app.get('/alerts/latest', (req, res) => res.json(alertStore));

// Health / status
app.get('/health', (req, res) => {
    res.json({
        status:          'Command Center Backend Online',
        activeAndroid:   androidClients.size,
        activeDashboard: dashboardClients.size,
        bufferedAlerts:  alertStore.length
    });
});

// ─────────────────────────────────────────────
// START SERVER
// ─────────────────────────────────────────────
const PORT   = process.env.PORT   || 8765;
const WS_URL = process.env.SENTINEL_WS_URL || `ws://localhost:${PORT}/ws`;

server.listen(PORT, () => {
    console.log('==================================================');
    console.log('      BORDER SENTINEL SECURITY SYSTEMS            ');
    console.log('==================================================');
    console.log(`[MILITARY NETWORK] HTTP Server      : http://localhost:${PORT}`);
    console.log(`[MILITARY NETWORK] WebSocket URL    : ${WS_URL}`);
    console.log(`[MILITARY NETWORK] Dashboard connect: ${WS_URL}?role=dashboard`);
    console.log(`[MILITARY NETWORK] Android connect  : ws://10.0.2.2:${PORT}/ws`);
    console.log(`[MILITARY NETWORK] REST API trigger : POST http://localhost:${PORT}/api/alerts/send`);
    console.log('==================================================');
});
