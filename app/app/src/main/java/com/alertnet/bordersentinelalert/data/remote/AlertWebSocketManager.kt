package com.alertnet.bordersentinelalert.data.remote

import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class WebSocketState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED
}

@Singleton
class AlertWebSocketManager @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private var webSocket: WebSocket? = null
    
    // DEV CONFIG: ws://10.0.2.2:8765/ws for Emulator | ws://10.227.1.32:8765/ws for physical device
    private val socketUrl = "ws://10.227.1.32:8765/ws"
    

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()
    
    var onAlertReceived: ((AlertEntity) -> Unit)? = null
    
    val isConnected: Boolean
        get() = _connectionState.value == WebSocketState.CONNECTED

    private var retryDelay = 2000L
    private val maxRetryDelay = 60000L

    fun connect() {
        if (_connectionState.value == WebSocketState.CONNECTED || _connectionState.value == WebSocketState.CONNECTING) return
        
        _connectionState.value = WebSocketState.CONNECTING
        Timber.i("[MILITARY SHIELD] Directing secure line handshake to $socketUrl...")
        
        val request = Request.Builder()
            .url(socketUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = WebSocketState.CONNECTED
                retryDelay = 2000L
                Timber.i("[MILITARY SHIELD] Secure handshake complete. ONLINE node active.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.d("[MILITARY SHIELD] Secured packet received: $text")
                try {
                    val alert = gson.fromJson(text, AlertEntity::class.java)
                    onAlertReceived?.invoke(alert)
                } catch (e: Exception) {
                    Timber.e(e, "[MILITARY SHIELD] Packet deserialization breach - JSON corrupted!")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = WebSocketState.DISCONNECTED
                Timber.w("[MILITARY SHIELD] Connection closing gracefully: $code / $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = WebSocketState.DISCONNECTED
                Timber.e(t, "[MILITARY SHIELD] Secure line failure detected!")
                attemptReconnect()
            }
        })
    }

    private fun attemptReconnect() {
        if (_connectionState.value == WebSocketState.CONNECTED) return
        scope.launch {
            Timber.d("[MILITARY SHIELD] Executing secure line retry in ${retryDelay/1000}s...")
            delay(retryDelay)
            connect()
            retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
        }
    }

    fun disconnect() {
        Timber.i("[MILITARY SHIELD] Closing secure node line...")
        webSocket?.close(1000, "Secure shutdown requested")
        _connectionState.value = WebSocketState.DISCONNECTED
    }
}
