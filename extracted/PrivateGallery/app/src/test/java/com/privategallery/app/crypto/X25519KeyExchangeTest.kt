package com.privategallery.app.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class X25519KeyExchangeTest {
    private val exchange = X25519KeyExchange()

    @Test
    fun `both parties derive the same shared wrapping key`() {
        val alice = exchange.generateKeyPair()
        val bob = exchange.generateKeyPair()
        val salt = "folder-123".toByteArray()
        val info = "folder-key-wrap:folder-123".toByteArray()

        val aliceDerived = exchange.deriveSharedWrappingKey(alice.privateKey, bob.publicKey, salt, info)
        val bobDerived = exchange.deriveSharedWrappingKey(bob.privateKey, alice.publicKey, salt, info)

        assertArrayEquals(aliceDerived, bobDerived)
    }

    @Test
    fun `different context info yields different derived keys`() {
        val alice = exchange.generateKeyPair()
        val bob = exchange.generateKeyPair()
        val salt = "folder-123".toByteArray()

        val keyA = exchange.deriveSharedWrappingKey(alice.privateKey, bob.publicKey, salt, "context-A".toByteArray())
        val keyB = exchange.deriveSharedWrappingKey(alice.privateKey, bob.publicKey, salt, "context-B".toByteArray())

        assertFalse(keyA.contentEquals(keyB))
    }
}
