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

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val repository: AlertRepository
) : ViewModel() {

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
                alertTitle = "UNAUTHORIZED MOVEMENT",
                description = "Infrared sensor triggered at Sector 4.",
                latitude = 22.9487,
                longitude = 88.4562,
                threatLevel = "HIGH",
                confidence = 94,
                mapUrl = "https://maps.google.com/?q=22.9487,88.4562"
            )
            repository.insertAlert(alert)
        }
    }
}
