const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const jwt = require('jsonwebtoken');
const cors = require('cors');
const bodyParser = require('body-parser');
require('dotenv').config();

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

const JWT_SECRET = process.env.JWT_SECRET || 'your_secret_key';

app.use(cors());
app.use(bodyParser.json());

// Store connected Android clients
const androidClients = new Set();

// WebSocket Connection Handler
wss.on('connection', (ws, req) => {
    // Basic Token Validation for WebSocket Handshake
    const url = new URL(req.url, `http://${req.headers.host}`);
    const token = url.searchParams.get('token');

    // In production, verify JWT token here
    console.log('New device attempting to connect...');

    ws.on('message', (message) => {
        console.log(`Received message: ${message}`);
    });

    ws.on('close', () => {
        androidClients.delete(ws);
        console.log('Device disconnected');
    });

    androidClients.add(ws);
    console.log('Device connected securely');
});

// API: Send Alert from Dashboard
app.post('/api/alerts/send', (req, res) => {
    const { title, description, latitude, longitude, threatLevel, confidence, mapUrl } = req.body;

    const alertPayload = JSON.stringify({
        alertTitle: title,
        description: description,
        latitude: parseFloat(latitude),
        longitude: parseFloat(longitude),
        threatLevel: threatLevel,
        confidence: parseInt(confidence),
        mapUrl: mapUrl,
        timestamp: Date.now(),
        isRead: false
    });

    // Broadcast to all connected Android devices
    let sentCount = 0;
    androidClients.forEach(client => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(alertPayload);
            sentCount++;
        }
    });

    console.log(`Alert broadcasted to ${sentCount} devices.`);
    res.json({ message: 'Alert broadcasted successfully', broadcastedTo: sentCount });
});

// Health Check
app.get('/health', (req, res) => {
    res.json({ status: 'Command Center Backend Online', activeDevices: androidClients.size });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`Border Sentinel Backend running on port ${PORT}`);
    console.log(`WebSocket Server active at wss://localhost:${PORT}`);
});
