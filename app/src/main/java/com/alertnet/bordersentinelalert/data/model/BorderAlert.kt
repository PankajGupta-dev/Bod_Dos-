package com.alertnet.bordersentinelalert.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "alerts")
data class BorderAlert(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val message: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("threat_level")
    val threatLevel: String, // LOW, MEDIUM, HIGH, CRITICAL
    val confidence: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
