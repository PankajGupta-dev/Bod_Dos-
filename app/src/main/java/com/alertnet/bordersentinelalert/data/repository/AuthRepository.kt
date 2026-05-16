package com.alertnet.bordersentinelalert.data.repository

import com.alertnet.bordersentinelalert.util.SecurityManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val securityManager: SecurityManager
) {
    fun login(officerId: String, token: String) {
        securityManager.saveOfficerId(officerId)
        securityManager.saveAuthToken(token)
    }

    fun logout() {
        securityManager.clearAuthToken()
    }

    fun isLoggedIn(): Boolean {
        return securityManager.getAuthToken() != null
    }

    fun getOfficerId(): String? = securityManager.getOfficerId()
    
    fun getAuthToken(): String? = securityManager.getAuthToken()
    
    fun isBiometricEnabled(): Boolean = securityManager.isBiometricEnabled()
    
    fun setBiometricEnabled(enabled: Boolean) {
        securityManager.setBiometricEnabled(enabled)
    }
}
