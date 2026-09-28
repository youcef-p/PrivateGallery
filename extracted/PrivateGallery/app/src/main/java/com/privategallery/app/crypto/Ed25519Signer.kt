package com.privategallery.app.crypto

import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import javax.inject.Inject
import javax.inject.Singleton

data class Ed25519KeyPair(val publicKey: ByteArray, val privateKey: ByteArray)

/**
 * Device identity signatures — used to sign the login challenge from the auth server (proving
 * possession of the device's private key without ever transmitting it) and to sign folder-key
 * exchange payloads so a man-in-the-middled signaling relay cannot substitute its own public key.
 */
@Singleton
class Ed25519Signer @Inject constructor() {

    fun generateKeyPair(): Ed25519KeyPair {
        val priv = Ed25519Sign.KeyPair.newKeyPair()
        return Ed25519KeyPair(priv.publicKey, priv.privateKey)
    }

    fun sign(privateKey: ByteArray, data: ByteArray): ByteArray =
        Ed25519Sign(privateKey).sign(data)

    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean = try {
        Ed25519Verify(publicKey).verify(signature, data)
        true
    } catch (e: com.google.crypto.tink.subtle.Ed25519Verify.SignatureVerificationException) {
        false
    } catch (e: java.security.GeneralSecurityException) {
        false
    }
}
