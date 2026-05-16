package com.alertnet.bordersentinelalert.data.remote

import android.util.Log
import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertWebSocketManager @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private var webSocket: WebSocket? = null
    private val socketUrl = "wss://api.bordersentinel.com/ws/alerts" // Placeholder Secure WebSocket URL
    private val scope = CoroutineScope(Dispatchers.IO)
    
    var onAlertReceived: ((AlertEntity) -> Unit)? = null
    var isConnected = false
        private set

    fun connect() {
        val request = Request.Builder()
            .url(socketUrl)
            .addHeader("Authorization", "Bearer YOUR_JWT_TOKEN") // JWT Token Authentication
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d("WebSocket", "Connected to Secure Alert Server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val alert = gson.fromJson(text, AlertEntity::class.java)
                    onAlertReceived?.invoke(alert)
                } catch (e: Exception) {
                    Log.e("WebSocket", "Error parsing alert JSON: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d("WebSocket", "Closing: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e("WebSocket", "Failure: ${t.message}")
                attemptReconnect()
            }
        })
    }

    private fun attemptReconnect() {
        scope.launch {
            delay(5000) // Wait 5 seconds before reconnecting
            Log.d("WebSocket", "Attempting to reconnect...")
            connect()
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "App closed")
    }
}
