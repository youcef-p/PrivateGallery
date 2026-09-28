package com.privategallery.app.data.repository

import com.privategallery.app.domain.model.User
import kotlinx.coroutines.flow.Flow

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun register(username: String, email: String, password: String): AuthResult
    suspend fun login(usernameOrEmail: String, password: String): AuthResult
    suspend fun forgotPassword(email: String): Result<Unit>
    suspend fun resetPassword(resetToken: String, newPassword: String): Result<Unit>
    suspend fun logout()
    suspend fun isBiometricLockEnabled(): Boolean
    suspend fun setBiometricLockEnabled(enabled: Boolean)
}
