package com.privategallery.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the Android Keystore for the one job it's used for here: generating and
 * holding a hardware/TEE-backed AES-256-GCM "master wrapping key" that never leaves secure
 * hardware. This key is used only to wrap/unwrap other key material (DB passphrase, folder keys
 * at rest) — it is never used to directly encrypt media (that's Tink's job, see MediaEncryptor).
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun getOrCreateWrappingKey(alias: String): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // app-lock is a separate UX layer, see below
            .setIsStrongBoxBacked(supportsStrongBox())
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** Wrap arbitrary bytes (e.g. a DB passphrase, a folder key) with the Keystore key. */
    fun wrap(alias: String, plaintext: ByteArray): WrappedBytes {
        val key = getOrCreateWrappingKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return WrappedBytes(ciphertext, cipher.iv)
    }

    fun unwrap(alias: String, wrapped: WrappedBytes): ByteArray {
        val key = getOrCreateWrappingKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped.iv))
        return cipher.doFinal(wrapped.ciphertext)
    }

    private fun supportsStrongBox(): Boolean = try {
        android.os.Build.VERSION.SDK_INT >= 28 &&
            android.app.Application().packageManager
                ?.hasSystemFeature("android.hardware.strongbox_keystore") == true
    } catch (e: Exception) {
        false
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

data class WrappedBytes(val ciphertext: ByteArray, val iv: ByteArray)
