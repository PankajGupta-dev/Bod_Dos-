package com.alertnet.bordersentinelalert.data.remote

import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body

interface AlertApiService {
    @GET("alerts/latest")
    suspend fun getLatestAlerts(): List<AlertEntity>

    @POST("blockchain/verify")
    suspend fun verifyBlockchainHash(@Body body: Map<String, String>): VerificationResponse
}

data class VerificationResponse(
    val verified: Boolean,
    val message: String? = null
)
