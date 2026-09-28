package com.privategallery.app.crypto

import org.junit.Assert.*
import org.junit.Test

class AesGcmCipherTest {
    private val cipher = AesGcmCipher()

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val key = cipher.generateKey()
        val plaintext = "private message".toByteArray()
        val ciphertext = cipher.encrypt(key, plaintext)
        val decrypted = cipher.decrypt(key, ciphertext)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt with wrong key fails`() {
        val key = cipher.generateKey()
        val wrongKey = cipher.generateKey()
        val ciphertext = cipher.encrypt(key, "secret".toByteArray())
        assertThrows(Exception::class.java) { cipher.decrypt(wrongKey, ciphertext) }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val key = cipher.generateKey()
        val ciphertext = cipher.encrypt(key, "secret".toByteArray()).copyOf()
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1] + 1).toByte()
        assertThrows(Exception::class.java) { cipher.decrypt(key, ciphertext) }
    }

    @Test
    fun `associated data mismatch fails decryption`() {
        val key = cipher.generateKey()
        val ciphertext = cipher.encrypt(key, "secret".toByteArray(), "context-A".toByteArray())
        assertThrows(Exception::class.java) { cipher.decrypt(key, ciphertext, "context-B".toByteArray()) }
    }

    @Test
    fun `chunked encryption round trips and rejects reordered chunks`() {
        val key = cipher.generateKey()
        val context = "folder1:media1".toByteArray()
        val chunk0 = cipher.encryptChunk(key, 0, "hello ".toByteArray(), context)
        val chunk1 = cipher.encryptChunk(key, 1, "world".toByteArray(), context)

        val decrypted0 = cipher.decryptChunk(key, 0, chunk0, context)
        val decrypted1 = cipher.decryptChunk(key, 1, chunk1, context)
        assertEquals("hello ", String(decrypted0))
        assertEquals("world", String(decrypted1))

        assertThrows(Exception::class.java) { cipher.decryptChunk(key, 1, chunk0, context) }
    }
}
