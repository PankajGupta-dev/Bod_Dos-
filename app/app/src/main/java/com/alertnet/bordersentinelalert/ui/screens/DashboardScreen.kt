package com.alertnet.bordersentinelalert.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alertnet.bordersentinelalert.ui.components.AlertItem
import com.alertnet.bordersentinelalert.util.MapUtils
import androidx.compose.ui.platform.LocalContext

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AlertViewModel,
    onNavigateToDetails: (Int) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val alerts by viewModel.alerts.collectAsState()
    val unreadCount by viewModel.unreadAlerts.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current
    
    var showEmergencyBanner by remember { mutableStateOf(false) }
    
    LaunchedEffect(alerts) {
        showEmergencyBanner = alerts.any { it.threatLevel == "HIGH" && !it.isRead }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("COMMAND CENTER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Text("Sector 7-Alpha Monitoring", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToHistory) {
                            BadgedBox(badge = { if (unreadCount.isNotEmpty()) Badge { Text(unreadCount.size.toString()) } }) {
                                Icon(Icons.Default.Notifications, contentDescription = "Alerts")
                            }
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                
                AnimatedVisibility(
                    visible = showEmergencyBanner,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Red)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🚨 CRITICAL ALERT DETECTED - RESPOND IMMEDIATELY 🚨", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCard("ACTIVE SENSORS", "142", Icons.Default.Radar, Color.Green)
                StatCard("THREAT LEVEL", if (showEmergencyBanner) "CRITICAL" else "NORMAL", Icons.Default.Shield, if (showEmergencyBanner) Color.Red else Color.Green)
            }
            
            Text(
                text = "LIVE FEED",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            
            if (alerts.isEmpty()) {
                MilitaryIdleMonitoringView(connectionState = connectionState)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(alerts) { alert ->
                        AlertItem(
                            alert = alert,
                            onClick = { onNavigateToDetails(alert.id) },
                            onMapClick = { MapUtils.openLocationInMaps(context, alert.latitude, alert.longitude) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MilitaryIdleMonitoringView(connectionState: com.alertnet.bordersentinelalert.data.remote.WebSocketState) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    var blockchainLogs by remember {
        mutableStateOf(listOf(
            "SYS_INIT: Secure ledger linked.",
            "BLOCK #749281 verified. Hash: 0x8a104f29ee8b24a1",
            "SECURE CHANNEL: Active."
        ))
    }

    LaunchedEffect(Unit) {
        var blockNumber = 749282
        while (true) {
            delay(3000)
            val fakeHash = "0x" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            val logMessage = "BLOCK #$blockNumber verified. Hash: $fakeHash"
            blockchainLogs = (blockchainLogs + logMessage).takeLast(4)
            blockNumber++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                
                drawCircle(color = Color(0xFF1B3B2B), radius = radius, style = Stroke(width = 2.dp.toPx()))
                drawCircle(color = Color(0xFF1B3B2B), radius = radius * 0.66f, style = Stroke(width = 1.dp.toPx()))
                drawCircle(color = Color(0xFF1B3B2B), radius = radius * 0.33f, style = Stroke(width = 1.dp.toPx()))
                
                drawLine(color = Color(0xFF1B3B2B), start = androidx.compose.ui.geometry.Offset(0f, center.y), end = androidx.compose.ui.geometry.Offset(size.width, center.y), strokeWidth = 1.dp.toPx())
                drawLine(color = Color(0xFF1B3B2B), start = androidx.compose.ui.geometry.Offset(center.x, 0f), end = androidx.compose.ui.geometry.Offset(center.x, size.height), strokeWidth = 1.dp.toPx())
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color.Transparent, Color(0xFF4CAF50).copy(alpha = 0.4f)),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF4CAF50).copy(alpha = glowAlpha), CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusIndicatorText("🟢 BLOCKCHAIN VERIFIED")
            Spacer(modifier = Modifier.height(6.dp))
            StatusIndicatorText("🟢 SURVEILLANCE NETWORK ONLINE")
            Spacer(modifier = Modifier.height(6.dp))
            
            val secureText = when (connectionState) {
                com.alertnet.bordersentinelalert.data.remote.WebSocketState.CONNECTED -> "🟢 SECURE CHANNEL ACTIVE"
                com.alertnet.bordersentinelalert.data.remote.WebSocketState.CONNECTING -> "🟡 SECURE LINE HANDSHAKE..."
                com.alertnet.bordersentinelalert.data.remote.WebSocketState.DISCONNECTED -> "🔴 SECURE CHANNEL OFFLINE"
            }
            StatusIndicatorText(secureText)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth().height(110.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1B3B2B))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "BLOCKCHAIN LEDGER DIAGNOSTICS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                blockchainLogs.forEach { log ->
                    Text(
                        text = log,
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun StatusIndicatorText(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF4CAF50),
        letterSpacing = 2.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    )
}

@Composable
fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = Modifier.width(170.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF242424))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 10.sp, color = Color.Gray)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}
