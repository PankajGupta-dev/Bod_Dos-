package com.alertnet.bordersentinelalert.data.local.dao

import androidx.room.*
import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Delete
    suspend fun deleteAlert(alert: AlertEntity)

    @Query("SELECT * FROM border_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM border_alerts WHERE isRead = 0 ORDER BY timestamp DESC")
    fun getUnreadAlerts(): Flow<List<AlertEntity>>

    @Query("UPDATE border_alerts SET isRead = 1 WHERE id = :alertId")
    suspend fun markAsRead(alertId: Int)

    @Query("DELETE FROM border_alerts")
    suspend fun clearAllAlerts()
}
