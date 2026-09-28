package com.privategallery.app.crypto

import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin, intentional wrapper around Tink's AesGcmJce primitive. We deliberately do NOT hand-roll
 * AES-GCM: Tink handles correct nonce generation, ciphertext framing, and constant-time tag
 * verification for us. This class exists only to give the rest of the app a stable, testable
 * seam (and to make key handling explicit at call sites).
 */
@Singleton
class AesGcmCipher @Inject constructor() {

    /** Generates a fresh random 256-bit key, suitable for a folder key or per-file key. */
    fun generateKey(): ByteArray = Random.randBytes(32)

    /**
     * Encrypts [plaintext] with [key]. [associatedData] should bind context (e.g. folder id +
     * media id) so ciphertexts can't be replayed into the wrong context.
     */
    fun encrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val aead = AesGcmJce(key)
        return aead.encrypt(plaintext, associatedData)
    }

    fun decrypt(key: ByteArray, ciphertext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val aead = AesGcmJce(key)
        return aead.decrypt(ciphertext, associatedData)
    }

    /**
     * Chunked encryption for large media (see docs/SECURITY.md). Each chunk gets its own AEAD
     * call with associatedData = context || chunkIndex, so chunks cannot be reordered or spliced
     * across files without detection at decrypt time.
     */
    fun encryptChunk(key: ByteArray, chunkIndex: Int, chunk: ByteArray, context: ByteArray): ByteArray {
        val aad = context + intToBytes(chunkIndex)
        return encrypt(key, chunk, aad)
    }

    fun decryptChunk(key: ByteArray, chunkIndex: Int, ciphertextChunk: ByteArray, context: ByteArray): ByteArray {
        val aad = context + intToBytes(chunkIndex)
        return decrypt(key, ciphertextChunk, aad)
    }

    private fun intToBytes(i: Int): ByteArray = byteArrayOf(
        (i shr 24).toByte(), (i shr 16).toByte(), (i shr 8).toByte(), i.toByte()
    )
}
