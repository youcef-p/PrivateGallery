package com.privategallery.app.crypto

import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

const val CHUNK_SIZE = 1 * 1024 * 1024 // 1 MiB, per docs/SECURITY.md

data class EncryptedFileResult(val encryptedFile: File, val fileKeyWrapped: ByteArray, val chunkCount: Int)

/**
 * Streams a plaintext media file into an encrypted, chunked file on disk without ever holding the
 * whole plaintext or whole ciphertext in memory — required for large video. Each chunk is framed
 * as [4-byte big-endian ciphertext length][ciphertext], so a resumed transfer (see
 * MediaTransferWorker) can seek to a chunk boundary and re-derive its AAD deterministically from
 * (context, chunkIndex).
 */
@Singleton
class MediaEncryptor @Inject constructor(
    private val aesGcmCipher: AesGcmCipher,
    private val folderKeyManager: FolderKeyManager
) {

    fun encryptFile(
        plaintextFile: File,
        outputFile: File,
        folderKeyAlias: String,
        context: ByteArray
    ): EncryptedFileResult {
        val folderKey = folderKeyManager.unwrapFolderKey(folderKeyAlias)
        val fileKey = aesGcmCipher.generateKey()
        val fileKeyWrapped = aesGcmCipher.encrypt(folderKey, fileKey, context)

        var chunkIndex = 0
        outputFile.outputStream().use { out ->
            plaintextFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(CHUNK_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    val ciphertext = aesGcmCipher.encryptChunk(fileKey, chunkIndex, chunk, context)
                    out.write(intToBytes(ciphertext.size))
                    out.write(ciphertext)
                    chunkIndex++
                }
            }
        }
        // fileKey only ever lived in a local val — goes out of scope here, nothing to zero
        // explicitly needed beyond JVM GC since Kotlin ByteArrays aren't securely wiped anyway;
        // documented as a known limitation of JVM-based crypto rather than a false guarantee.
        return EncryptedFileResult(outputFile, fileKeyWrapped, chunkIndex)
    }

    fun decryptFile(
        encryptedFile: File,
        outputFile: File,
        folderKeyAlias: String,
        fileKeyWrapped: ByteArray,
        context: ByteArray
    ) {
        val folderKey = folderKeyManager.unwrapFolderKey(folderKeyAlias)
        val fileKey = aesGcmCipher.decrypt(folderKey, fileKeyWrapped, context)

        var chunkIndex = 0
        RandomAccessFile(encryptedFile, "r").use { raf ->
            outputFile.outputStream().use { out ->
                val lenBuf = ByteArray(4)
                while (raf.filePointer < raf.length()) {
                    raf.readFully(lenBuf)
                    val len = bytesToInt(lenBuf)
                    val ciphertext = ByteArray(len)
                    raf.readFully(ciphertext)
                    val plaintextChunk = aesGcmCipher.decryptChunk(fileKey, chunkIndex, ciphertext, context)
                    out.write(plaintextChunk)
                    chunkIndex++
                }
            }
        }
    }

    /** Decrypts and re-encrypts a single chunk index — used to verify a resumed upload's next chunk. */
    fun decryptSingleChunkAt(
        encryptedFile: File, chunkIndex: Int, fileKey: ByteArray, context: ByteArray, chunkByteOffset: Long
    ): ByteArray {
        RandomAccessFile(encryptedFile, "r").use { raf ->
            raf.seek(chunkByteOffset)
            val lenBuf = ByteArray(4)
            raf.readFully(lenBuf)
            val len = bytesToInt(lenBuf)
            val ciphertext = ByteArray(len)
            raf.readFully(ciphertext)
            return aesGcmCipher.decryptChunk(fileKey, chunkIndex, ciphertext, context)
        }
    }

    private fun intToBytes(i: Int): ByteArray = byteArrayOf(
        (i shr 24).toByte(), (i shr 16).toByte(), (i shr 8).toByte(), i.toByte()
    )
    private fun bytesToInt(b: ByteArray): Int =
        ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
}
