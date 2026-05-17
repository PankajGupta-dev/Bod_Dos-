package com.alertnet.bordersentinelalert.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alertnet.bordersentinelalert.ui.theme.BorderSentinelAlertTheme
import com.alertnet.bordersentinelalert.util.SoundUtils
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

class EmergencyAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val title = intent.getStringExtra("EXTRA_TITLE") ?: "BORDER ALERT"
        val desc = intent.getStringExtra("EXTRA_DESC") ?: "Unauthorized movement detected!"
        val imageUrl = intent.getStringExtra("EXTRA_IMAGE")

        setContent {
            BorderSentinelAlertTheme {
                EmergencyScreen(title, desc, imageUrl) {
                    SoundUtils.stopBuzzer()
                    finish()
                }
            }
        }
    }
}

@Composable
fun EmergencyScreen(title: String, desc: String, imageUrl: String?, onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "Flash")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Red flashing background overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Red.copy(alpha = alpha))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Surveillance Snapshot
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(MaterialTheme.shapes.large),
                color = Color.DarkGray,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                if (!imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Surveillance Snapshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text("NO SNAPSHOT AVAILABLE", color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Detailed Description
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = desc,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    lineHeight = 24.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Action Button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "ACKNOWLEDGE & DISMISS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
