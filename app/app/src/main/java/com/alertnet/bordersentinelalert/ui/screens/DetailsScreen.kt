package com.alertnet.bordersentinelalert.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alertnet.bordersentinelalert.ui.components.EmbeddedMap
import com.alertnet.bordersentinelalert.util.MapUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(alertId: Int, viewModel: AlertViewModel, onNavigateBack: () -> Unit) {
    val alerts by viewModel.alerts.collectAsState()
    val alert = alerts.find { it.id == alertId }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    if (alert == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Alert not found.")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MISSION INTEL") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (alert.threatLevel == "HIGH") Color.Red else MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Embedded Map Preview
            Text(
                text = "GEOGRAPHIC VISUALIZATION",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            EmbeddedMap(alert.latitude, alert.longitude)
            
            Spacer(modifier = Modifier.height(24.dp))

            // Intel Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF242424))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = alert.alertType,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = if (alert.threatLevel == "HIGH") Color.Red else Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CAMERA: ${alert.cameraId} • COORD: ${alert.latitude}, ${alert.longitude}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow("THREAT LEVEL", alert.threatLevel, if (alert.threatLevel == "HIGH") Color.Red else Color.Yellow)
            DetailRow("CONFIDENCE", "${(alert.confidence * 100).toInt()}%", if (alert.confidence > 0.9) Color.Green else Color.Yellow)
            DetailRow("BLOCKCHAIN", when(alert.blockchainStatus) {
                "SUCCESS" -> "✓ VERIFIED"
                "FAILED" -> "⚠ TAMPERED"
                else -> "VERIFYING..."
            }, when(alert.blockchainStatus) {
                "SUCCESS" -> Color.Green
                "FAILED" -> Color.Red
                else -> Color.Cyan
            })
            
            Spacer(modifier = Modifier.height(16.dp))

            Text("SITUATION REPORT", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            
            val situationReport = if (!alert.personName.isNullOrEmpty()) {
                "Subject identified as ${alert.personName}. Detected by ${alert.cameraId} with high confidence. Data hash: ${alert.blockchainHash ?: "N/A"}"
            } else {
                "Unknown subject ${alert.unknownId ?: "N/A"} detected at perimeter. Visual confirmation required. Data hash: ${alert.blockchainHash ?: "N/A"}"
            }
            
            Text(
                text = situationReport,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { MapUtils.openLocationInMaps(context, alert.latitude, alert.longitude) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("INITIATE NAVIGATION", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = { viewModel.markAsRead(alert.id); onNavigateBack() },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("CLOSE MISSION")
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
    HorizontalDivider(color = Color(0xFF2E2E2E), thickness = 0.5.dp)
}
