package com.alertnet.bordersentinelalert.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import com.alertnet.bordersentinelalert.ui.theme.EmergencyRed
import com.alertnet.bordersentinelalert.ui.theme.HighConfidence
import com.alertnet.bordersentinelalert.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AlertItem(
    alert: AlertEntity,
    onClick: () -> Unit,
    onMapClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = when (alert.threatLevel) {
                            "HIGH" -> EmergencyRed
                            "MEDIUM" -> WarningOrange
                            else -> Color.Gray
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alert. alertType,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                }
                Text(
                    text = sdf.format(java.util.Date(alert.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            val subjectText = if (!alert.personName.isNullOrEmpty()) {
                "Identified: ${alert.personName}"
            } else {
                "Unknown ID: ${alert.unknownId ?: "N/A"}"
            }
            
            Text(
                text = "$subjectText • Cam: ${alert.cameraId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Blockchain Status Badge
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = when (alert.blockchainStatus) {
                    "SUCCESS" -> Color(0xFFE8F5E9)
                    "FAILED" -> Color(0xFFFFEBEE)
                    else -> Color(0xFFE3F2FD)
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = when (alert.blockchainStatus) {
                        "SUCCESS" -> "✓ VERIFIED"
                        "FAILED" -> "⚠ TAMPERED"
                        else -> "VERIFYING..."
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (alert.blockchainStatus) {
                        "SUCCESS" -> Color(0xFF2E7D32)
                        "FAILED" -> Color(0xFFC62828)
                        else -> Color(0xFF1565C0)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Confidence: ${(alert.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (alert.confidence > 0.9) HighConfidence else Color(0xFFFBC02D)
                    )
                    Text(
                        text = "Threat: ${alert.threatLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                Button(
                    onClick = onMapClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("MAP", fontSize = 12.sp)
                }
            }
        }
    }
}
