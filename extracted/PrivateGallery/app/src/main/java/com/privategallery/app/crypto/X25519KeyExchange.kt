package com.privategallery.app.crypto

import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import javax.inject.Inject
import javax.inject.Singleton

data class X25519KeyPair(val publicKey: ByteArray, val privateKey: ByteArray)

/**
 * X25519 ECDH key agreement + HKDF, used once per folder invite to derive the key that wraps the
 * folder's symmetric key (see docs/SECURITY.md § Folder key establishment). Private key material
 * returned here is held only transiently in memory by the caller and immediately wrapped via
 * KeystoreManager before persistence — never stored raw.
 */
@Singleton
class X25519KeyExchange @Inject constructor() {

    fun generateKeyPair(): X25519KeyPair {
        val priv = X25519.generatePrivateKey()
        val pub = X25519.publicFromPrivate(priv)
        return X25519KeyPair(pub, priv)
    }

    /**
     * Derives a 256-bit symmetric wrapping key from our private key and the peer's public key,
     * with an explicit HKDF `info` binding the two user ids so the derived key is
     * context-specific and cannot be reused across relationships.
     */
    fun deriveSharedWrappingKey(
        ourPrivateKey: ByteArray,
        peerPublicKey: ByteArray,
        salt: ByteArray,
        contextInfo: ByteArray
    ): ByteArray {
        val sharedSecret = X25519.computeSharedSecret(ourPrivateKey, peerPublicKey)
        return Hkdf.computeHkdf("HMACSHA256", sharedSecret, salt, contextInfo, 32)
    }
}
