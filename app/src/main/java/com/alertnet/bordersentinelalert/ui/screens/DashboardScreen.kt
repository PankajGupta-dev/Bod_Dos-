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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No activity detected.", color = Color.DarkGray)
                }
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
