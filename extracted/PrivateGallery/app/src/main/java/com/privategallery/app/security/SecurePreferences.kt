package com.privategallery.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.privategallery.app.crypto.Ed25519KeyPair
import com.privategallery.app.crypto.X25519KeyPair
import com.privategallery.app.crypto.fromBase64
import com.privategallery.app.crypto.toBase64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds session tokens, device identity, and (wrapped) device private keys. Backed by
 * EncryptedSharedPreferences, whose master key is itself Keystore-resident — so this is the
 * "wrapped at rest, unwrapped only in process memory" pattern used throughout the app.
 *
 * Nothing in here is ever logged (see NetworkModule's logging interceptor, which redacts
 * Authorization headers and never logs bodies containing key material).
 */
@Singleton
class SecurePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "private_gallery_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _currentUserIdFlow = MutableStateFlow(prefs.getString(KEY_CURRENT_USER_ID, null))
    val currentUserIdFlow: StateFlow<String?> = _currentUserIdFlow.asStateFlow()

    fun getOrCreateDeviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun storeDeviceKeys(x25519: X25519KeyPair, ed25519: Ed25519KeyPair) {
        prefs.edit()
            .putString(KEY_X25519_PRIV, x25519.privateKey.toBase64())
            .putString(KEY_X25519_PUB, x25519.publicKey.toBase64())
            .putString(KEY_ED25519_PRIV, ed25519.privateKey.toBase64())
            .putString(KEY_ED25519_PUB, ed25519.publicKey.toBase64())
            .apply()
    }

    /** Returns (x25519PrivateKey, ed25519PrivateKey) or null if this device has never registered. */
    fun getDeviceKeys(): Pair<ByteArray, ByteArray>? {
        val x = prefs.getString(KEY_X25519_PRIV, null) ?: return null
        val ed = prefs.getString(KEY_ED25519_PRIV, null) ?: return null
        return x.fromBase64() to ed.fromBase64()
    }

    fun storeTokens(accessToken: String, refreshToken: String, expiresAt: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun setCurrentUserId(userId: String) {
        prefs.edit().putString(KEY_CURRENT_USER_ID, userId).apply()
        _currentUserIdFlow.value = userId
    }

    fun isBiometricLockEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)
    fun setBiometricLockEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply() }

    fun isScreenshotProtectionEnabled(): Boolean = prefs.getBoolean(KEY_SCREENSHOT_PROTECTION, true)
    fun setScreenshotProtectionEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, enabled).apply() }

    /** Clears session tokens and current-user pointer but retains device keys (needed to
     *  re-login on this device without losing folder-key derivation ability). Full key wipe is a
     *  separate, explicit "delete account / forget device" action. */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_CURRENT_USER_ID)
            .apply()
        _currentUserIdFlow.value = null
    }

    fun wipeAllDeviceKeyMaterial() {
        prefs.edit().clear().apply()
        _currentUserIdFlow.value = null
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_X25519_PRIV = "x25519_priv"
        private const val KEY_X25519_PUB = "x25519_pub"
        private const val KEY_ED25519_PRIV = "ed25519_priv"
        private const val KEY_ED25519_PUB = "ed25519_pub"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_CURRENT_USER_ID = "current_user_id"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        private const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection_enabled"
    }
}
