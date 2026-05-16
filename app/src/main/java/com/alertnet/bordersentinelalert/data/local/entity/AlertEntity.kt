package com.alertnet.bordersentinelalert.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "border_alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val alertTitle: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val threatLevel: String, // e.g., "HIGH", "MEDIUM", "LOW"
    val confidence: Int,
    val mapUrl: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
