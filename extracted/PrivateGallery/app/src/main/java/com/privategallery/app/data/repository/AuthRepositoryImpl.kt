package com.privategallery.app.data.repository

import com.privategallery.app.crypto.Ed25519Signer
import com.privategallery.app.crypto.X25519KeyExchange
import com.privategallery.app.crypto.toBase64
import com.privategallery.app.data.local.dao.UserDao
import com.privategallery.app.data.local.entity.UserEntity
import com.privategallery.app.data.remote.api.AuthApi
import com.privategallery.app.data.remote.dto.*
import com.privategallery.app.domain.model.User
import com.privategallery.app.security.SecurePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NOTE on the password + key flows: the account password authenticates the *account* (rate
 * limited, Argon2id-hashed server-side, recoverable via email). It is deliberately independent
 * from the X25519/Ed25519 device keys that gate E2E folder content — this is why "forgot
 * password" can reset account access without silently granting the server, or an attacker who
 * resets a password, access to already-established folder keys. The UI must say this explicitly
 * (see ForgotPasswordScreen).
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val userDao: UserDao,
    private val securePreferences: SecurePreferences,
    private val ed25519Signer: Ed25519Signer,
    private val x25519KeyExchange: X25519KeyExchange
) : AuthRepository {

    override val currentUser: Flow<User?> = securePreferences.currentUserIdFlow.map { userId ->
        userId?.let { userDao.getUser(it) }?.toDomain()
    }

    override suspend fun register(username: String, email: String, password: String): AuthResult {
        return try {
            val deviceId = securePreferences.getOrCreateDeviceId()
            val x25519Pair = x25519KeyExchange.generateKeyPair()
            val ed25519Pair = ed25519Signer.generateKeyPair()

            // Private keys are handed to SecurePreferences, which wraps them via KeystoreManager
            // before writing anything to disk — never persisted raw, never sent in this request.
            securePreferences.storeDeviceKeys(x25519Pair, ed25519Pair)

            val response = authApi.register(
                RegisterRequest(
                    username = username,
                    email = email,
                    password = password,
                    publicKeyX25519 = x25519Pair.publicKey.toBase64(),
                    publicKeyEd25519 = ed25519Pair.publicKey.toBase64(),
                    deviceId = deviceId
                )
            )
            persistSession(response, username, email, deviceId, x25519Pair.publicKey, ed25519Pair.publicKey)
            AuthResult.Success(User(response.userId, username, email, deviceId))
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Registration failed")
        }
    }

    override suspend fun login(usernameOrEmail: String, password: String): AuthResult {
        return try {
            val challenge = authApi.requestLoginChallenge(LoginChallengeRequest(usernameOrEmail))
            val (_, ed25519Private) = securePreferences.getDeviceKeys()
                ?: return AuthResult.Failure(
                    "No device keys found for this account on this device. If you're signing in " +
                        "on a new device, your partner's folders will need a fresh invite — " +
                        "previously encrypted content tied to the old device's keys cannot be " +
                        "recovered here."
                )
            val signature = ed25519Signer.sign(ed25519Private, challenge.challenge.toByteArray())
            val response = authApi.login(
                LoginRequest(
                    usernameOrEmail = usernameOrEmail,
                    password = password,
                    challengeId = challenge.challengeId,
                    signedChallenge = signature.toBase64()
                )
            )
            val deviceId = securePreferences.getOrCreateDeviceId()
            persistSession(response, usernameOrEmail, usernameOrEmail, deviceId, null, null)
            AuthResult.Success(User(response.userId, usernameOrEmail, usernameOrEmail, deviceId))
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Login failed")
        }
    }

    override suspend fun forgotPassword(email: String): Result<Unit> = runCatching {
        authApi.forgotPassword(ForgotPasswordRequest(email))
    }

    override suspend fun resetPassword(resetToken: String, newPassword: String): Result<Unit> = runCatching {
        authApi.resetPassword(ResetPasswordRequest(resetToken, newPassword))
    }

    override suspend fun logout() {
        securePreferences.clearSession()
        userDao.clearAll()
    }

    override suspend fun isBiometricLockEnabled(): Boolean = securePreferences.isBiometricLockEnabled()
    override suspend fun setBiometricLockEnabled(enabled: Boolean) = securePreferences.setBiometricLockEnabled(enabled)

    private suspend fun persistSession(
        response: AuthResponse, username: String, email: String, deviceId: String,
        pubX: ByteArray?, pubEd: ByteArray?
    ) {
        securePreferences.storeTokens(response.accessToken, response.refreshToken, response.expiresAt)
        securePreferences.setCurrentUserId(response.userId)
        userDao.upsert(
            UserEntity(
                id = response.userId, username = username, email = email,
                publicKeyX25519 = pubX?.toBase64() ?: "", publicKeyEd25519 = pubEd?.toBase64() ?: "",
                deviceId = deviceId, createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun UserEntity.toDomain() = User(id, username, email, deviceId)
}
