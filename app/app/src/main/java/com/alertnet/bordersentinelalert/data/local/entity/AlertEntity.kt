package com.alertnet.bordersentinelalert.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "border_alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @SerializedName("alert_type")
    val alertType: String,
    @SerializedName("person_name")
    val personName: String?,
    @SerializedName("unknown_id")
    val unknownId: String?,
    @SerializedName("confidence")
    val confidence: Double,
    @SerializedName("snapshot_url")
    val snapshotUrl: String,
    @SerializedName("blockchain_verified")
    val blockchainVerified: Boolean,
    @SerializedName("blockchain_hash")
    val blockchainHash: String?,
    @SerializedName("camera_id")
    val cameraId: String,
    @SerializedName("threat_level")
    val threatLevel: String, // e.g., "HIGH", "MEDIUM", "LOW"
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val blockchainStatus: String = "PENDING", // PENDING, SUCCESS, FAILED
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
