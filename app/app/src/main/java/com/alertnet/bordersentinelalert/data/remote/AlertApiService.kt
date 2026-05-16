package com.alertnet.bordersentinelalert.data.remote

import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import retrofit2.http.GET

interface AlertApiService {
    @GET("alerts/latest")
    suspend fun getLatestAlerts(): List<AlertEntity>
}
