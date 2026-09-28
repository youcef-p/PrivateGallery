package com.privategallery.app.crypto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ed25519SignerTest {
    private val signer = Ed25519Signer()

    @Test
    fun `valid signature verifies`() {
        val pair = signer.generateKeyPair()
        val data = "login-challenge-nonce".toByteArray()
        val signature = signer.sign(pair.privateKey, data)
        assertTrue(signer.verify(pair.publicKey, data, signature))
    }

    @Test
    fun `signature from wrong key fails verification`() {
        val pair = signer.generateKeyPair()
        val otherPair = signer.generateKeyPair()
        val data = "login-challenge-nonce".toByteArray()
        val signature = signer.sign(pair.privateKey, data)
        assertFalse(signer.verify(otherPair.publicKey, data, signature))
    }

    @Test
    fun `tampered data fails verification`() {
        val pair = signer.generateKeyPair()
        val signature = signer.sign(pair.privateKey, "original".toByteArray())
        assertFalse(signer.verify(pair.publicKey, "tampered".toByteArray(), signature))
    }
}
