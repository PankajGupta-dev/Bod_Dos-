package com.alertnet.bordersentinelalert.data

import com.alertnet.bordersentinelalert.data.local.dao.AlertDao
import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the local Room database with demo surveillance alerts every time the app opens.
 * These match the THREAT LOG format from the web dashboard.
 * All are pre-verified with blockchain status SUCCESS and use current IST time.
 */
@Singleton
class DemoAlertSeeder @Inject constructor(
    private val alertDao: AlertDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Realistic surveillance snapshot images
    private val snapshots = listOf(
        "https://images.unsplash.com/photo-1579353977828-2a4eab540b9a?w=500",
        "https://images.unsplash.com/photo-1508614999368-9260051292e5?w=500",
        "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=500",
        "https://images.unsplash.com/photo-1545906770-cb9f08c26e17?w=500",
        "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=500"
    )

    fun seedDemoAlerts() {
        scope.launch {
            // Clear previous demo data so we always show fresh on open
            alertDao.clearAllAlerts()

            val currentTime = System.currentTimeMillis()

            val demoAlerts = listOf(
                // From the image: SEC-CHARLIE | Unidentified Movement | CAM-02 | Time: Current IST Time
                AlertEntity(
                    alertType    = "SEC-CHARLIE",
                    personName   = "Unidentified Movement",
                    unknownId    = "UNK_03",
                    confidence   = 0.91,
                    snapshotUrl  = snapshots[0],
                    blockchainVerified = true,
                    blockchainHash     = generateHash("SEC-CHARLIE-CAM02-CURRENT"),
                    cameraId     = "CAM-02",
                    threatLevel  = "HIGH",
                    latitude     = 22.6521,
                    longitude    = 88.4191,
                    blockchainStatus = "SUCCESS",
                    timestamp    = currentTime, // Current IST Time (0 minutes ago)
                    isRead       = false
                ),
                // From the image: SEC-ALPHA | 1 Human | CAM-01 | Time: 2 minutes ago
                AlertEntity(
                    alertType    = "SEC-ALPHA",
                    personName   = "1 Human",
                    unknownId    = "UNK_01",
                    confidence   = 0.87,
                    snapshotUrl  = snapshots[1],
                    blockchainVerified = true,
                    blockchainHash     = generateHash("SEC-ALPHA-CAM01-CURRENT-2MIN"),
                    cameraId     = "CAM-01",
                    threatLevel  = "MEDIUM",
                    latitude     = 22.6521,
                    longitude    = 88.4191,
                    blockchainStatus = "SUCCESS",
                    timestamp    = currentTime - (2 * 60 * 1000), // 2 minutes ago
                    isRead       = false
                ),
                // From the image: SEC-ALPHA | 1 Human Detected | CAM-01 | Time: 5 minutes ago
                AlertEntity(
                    alertType    = "SEC-ALPHA",
                    personName   = "1 Human Detected",
                    unknownId    = "UNK_02",
                    confidence   = 0.93,
                    snapshotUrl  = snapshots[2],
                    blockchainVerified = true,
                    blockchainHash     = generateHash("SEC-ALPHA-DET-CAM01-CURRENT-5MIN"),
                    cameraId     = "CAM-01",
                    threatLevel  = "HIGH",
                    latitude     = 22.6521,
                    longitude    = 88.4191,
                    blockchainStatus = "SUCCESS",
                    timestamp    = currentTime - (5 * 60 * 1000), // 5 minutes ago
                    isRead       = false
                ),
                // Extra demo: WEAPON DETECTED | Time: 12 minutes ago
                AlertEntity(
                    alertType    = "WEAPON DETECTED",
                    personName   = "Armed Intruder",
                    unknownId    = "UNK_04",
                    confidence   = 0.97,
                    snapshotUrl  = snapshots[3],
                    blockchainVerified = true,
                    blockchainHash     = generateHash("WEAPON-DRONE05-CURRENT-12MIN"),
                    cameraId     = "DRONE-05",
                    threatLevel  = "CRITICAL",
                    latitude     = 22.6521,
                    longitude    = 88.4191,
                    blockchainStatus = "SUCCESS",
                    timestamp    = currentTime - (12 * 60 * 1000), // 12 minutes ago
                    isRead       = false
                ),
                // Extra demo: FIRE DETECTED | Time: 30 minutes ago
                AlertEntity(
                    alertType    = "FIRE DETECTED",
                    personName   = null,
                    unknownId    = "ENV_01",
                    confidence   = 0.88,
                    snapshotUrl  = snapshots[4],
                    blockchainVerified = true,
                    blockchainHash     = generateHash("FIRE-CAM03-CURRENT-30MIN"),
                    cameraId     = "CAM-03",
                    threatLevel  = "HIGH",
                    latitude     = 22.6521,
                    longitude    = 88.4191,
                    blockchainStatus = "SUCCESS",
                    timestamp    = currentTime - (30 * 60 * 1000), // 30 minutes ago
                    isRead       = false
                )
            )

            demoAlerts.forEach { alertDao.insertAlert(it) }
        }
    }

    /** SHA-256 hash generator — simulates real blockchain verification */
    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
