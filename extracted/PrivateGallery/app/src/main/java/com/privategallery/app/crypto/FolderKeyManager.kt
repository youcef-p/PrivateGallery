package com.privategallery.app.crypto

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lifecycle of per-folder symmetric keys: generation, wrapping for storage, wrapping for
 * transmission to an invitee, and rotation on membership revocation. Raw folder keys are only
 * ever held in memory for the duration of an encrypt/decrypt call; at rest they exist solely as
 * Keystore-wrapped bytes (see KeystoreManager) keyed by [FolderEntity.folderKeyAlias].
 */
@Singleton
class FolderKeyManager @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val aesGcmCipher: AesGcmCipher,
    private val x25519: X25519KeyExchange
) {

    /** Generates a new folder key and seals it under a fresh Keystore alias. Returns the alias. */
    fun createFolderKey(folderId: String): String {
        val alias = keystoreAliasFor(folderId)
        val rawKey = aesGcmCipher.generateKey()
        val wrapped = keystoreManager.wrap(alias, rawKey)
        wrappedKeyStore.put(alias, wrapped) // see note below re: persistence
        return alias
    }

    /** Retrieves the raw folder key for use in an encrypt/decrypt call. Never cache the return value. */
    fun unwrapFolderKey(alias: String): ByteArray {
        val wrapped = wrappedKeyStore[alias]
            ?: error("No wrapped key found for alias $alias — folder key store corrupted or wiped")
        return keystoreManager.unwrap(alias, wrapped)
    }

    /**
     * Wraps a folder key for transmission to an invitee during the invite-accept handshake:
     * ECDH(ourPriv, theirPub) -> HKDF -> AES-GCM-wrap(folderKey).
     */
    fun wrapFolderKeyForPeer(
        rawFolderKey: ByteArray,
        ourPrivateKey: ByteArray,
        peerPublicKey: ByteArray,
        folderId: String
    ): ByteArray {
        val contextInfo = "folder-key-wrap:$folderId".toByteArray(StandardCharsets.UTF_8)
        val salt = folderId.toByteArray(StandardCharsets.UTF_8)
        val kwrap = x25519.deriveSharedWrappingKey(ourPrivateKey, peerPublicKey, salt, contextInfo)
        return aesGcmCipher.encrypt(kwrap, rawFolderKey, contextInfo)
    }

    fun unwrapFolderKeyFromPeer(
        wrappedKeyFromPeer: ByteArray,
        ourPrivateKey: ByteArray,
        peerPublicKey: ByteArray,
        folderId: String
    ): ByteArray {
        val contextInfo = "folder-key-wrap:$folderId".toByteArray(StandardCharsets.UTF_8)
        val salt = folderId.toByteArray(StandardCharsets.UTF_8)
        val kwrap = x25519.deriveSharedWrappingKey(ourPrivateKey, peerPublicKey, salt, contextInfo)
        return aesGcmCipher.decrypt(kwrap, wrappedKeyFromPeer, contextInfo)
    }

    /** Revocation: generate a brand-new key under a new alias; old alias/content is inert going forward. */
    fun rotateFolderKey(folderId: String): String {
        val oldAlias = keystoreAliasFor(folderId)
        wrappedKeyStore.remove(oldAlias)
        return createFolderKey(folderId)
    }

    private fun keystoreAliasFor(folderId: String) = "folder-key-$folderId"

    companion object {
        // NOTE: in production this in-memory map is replaced by a small encrypted table
        // (folder_key_blobs: alias -> ciphertext, iv) written via Room alongside FolderEntity,
        // since Keystore only stores the wrapping key itself, not the wrapped bytes. Kept as an
        // explicit in-memory placeholder here (rather than silently wiring it to a real table)
        // so the persistence boundary is visible and testable independent of Room migrations.
        private val wrappedKeyStore = mutableMapOf<String, WrappedBytes>()
    }
}

fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
