package com.alertnet.bordersentinelalert.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import com.alertnet.bordersentinelalert.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.alertnet.bordersentinelalert.data.remote.AlertWebSocketManager
import com.alertnet.bordersentinelalert.data.remote.WebSocketState

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val repository: AlertRepository,
    val webSocketManager: AlertWebSocketManager
) : ViewModel() {

    val connectionState: StateFlow<WebSocketState> = webSocketManager.connectionState

    val alerts: StateFlow<List<AlertEntity>> = repository.allAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadAlerts: StateFlow<List<AlertEntity>> = repository.unreadAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markAsRead(id: Int) {
        viewModelScope.launch {
            repository.markAsRead(id)
        }
    }

    fun clearAllAlerts() {
        viewModelScope.launch {
            repository.clearAllAlerts()
        }
    }

    fun simulateAlert() {
        viewModelScope.launch {
            val alert = AlertEntity(
                alertType = "INTRUSION",
                personName = null,
                unknownId = "UNK_SIM_01",
                confidence = 0.94,
                snapshotUrl = "https://example.com/snapshot.jpg",
                blockchainVerified = true,
                blockchainHash = "simulated_hash_123",
                cameraId = "SIM_CAM_01",
                threatLevel = "HIGH"
            )
            repository.insertAlert(alert)
        }
    }
}
