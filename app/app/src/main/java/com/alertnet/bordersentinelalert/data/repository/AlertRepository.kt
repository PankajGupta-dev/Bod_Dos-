package com.alertnet.bordersentinelalert.data.repository

import com.alertnet.bordersentinelalert.data.local.dao.AlertDao
import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val alertDao: AlertDao
) {
    val allAlerts: Flow<List<AlertEntity>> = alertDao.getAllAlerts()
    val unreadAlerts: Flow<List<AlertEntity>> = alertDao.getUnreadAlerts()

    suspend fun insertAlert(alert: AlertEntity) {
        alertDao.insertAlert(alert)
    }

    suspend fun deleteAlert(alert: AlertEntity) {
        alertDao.deleteAlert(alert)
    }

    suspend fun markAsRead(alertId: Int) {
        alertDao.markAsRead(alertId)
    }

    suspend fun updateBlockchainStatus(alertId: Int, status: String) {
        alertDao.updateBlockchainStatus(alertId, status)
    }

    suspend fun clearAllAlerts() {
        alertDao.clearAllAlerts()
    }
}
