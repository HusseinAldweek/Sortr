package com.sortr.data.crypto

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultCryptoException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * AES-256-GCM encryption of vault files with a per-file data key.
 *
 * File layout: `SVF1` magic (4 bytes) | IV (12 bytes) | ciphertext | GCM tag (16 bytes).
 * The magic is authenticated as associated data, so any change to the file fails decryption.
 */
object VaultCipher {

    const val KEY_BYTES = 32
    const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), 'F'.code.toByte(), '1'.code.toByte())
    private val random = SecureRandom()

    fun newDataKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    /** Streams [input] into [output] encrypted with [key]. Returns the random IV used. */
    fun encrypt(input: InputStream, output: OutputStream, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, key, iv)
        output.write(MAGIC)
        output.write(iv)
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            cipher.update(buffer, 0, read)?.let { if (it.isNotEmpty()) output.write(it) }
        }
        output.write(cipher.doFinal())
        output.flush()
        return iv
    }

    fun encrypt(plaintext: ByteArray, output: OutputStream, key: ByteArray): ByteArray =
        encrypt(plaintext.inputStream(), output, key)

    /**
     * Decrypts data written by [encrypt] into [output]. Throws [VaultCryptoException] if the key
     * is wrong or the data has been changed or cut short. Plaintext is only released after the
     * whole file has been authenticated.
     */
    fun decrypt(input: InputStream, output: OutputStream, key: ByteArray) {
        decryptToBuffer(input, key, sizeHint = 0).writeTo(output)
    }

    /** Decrypts into memory. [sizeHint] (e.g. the encrypted file's size) avoids regrowing the buffer. */
    fun decrypt(input: InputStream, key: ByteArray, sizeHint: Long = 0): ByteArray =
        decryptToBuffer(input, key, sizeHint).toByteArray()

    private fun decryptToBuffer(input: InputStream, key: ByteArray, sizeHint: Long): ByteArrayOutputStream {
        val magic = input.readExactly(MAGIC.size)
        if (!magic.contentEquals(MAGIC)) throw VaultCryptoException("Not a vault file")
        val iv = input.readExactly(IV_BYTES)
        val cipher = cipher(Cipher.DECRYPT_MODE, key, iv)
        // Collect the plaintext and only hand it over once the tag has been checked.
        val plain = ByteArrayOutputStream(sizeHint.coerceIn(BUFFER_BYTES.toLong(), Int.MAX_VALUE.toLong() - 8).toInt())
        val buffer = ByteArray(BUFFER_BYTES)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                cipher.update(buffer, 0, read)?.let { if (it.isNotEmpty()) plain.write(it) }
            }
            plain.write(cipher.doFinal())
        } catch (e: GeneralSecurityException) {
            throw VaultCryptoException("Vault file failed authentication", e)
        }
        return plain
    }

    private fun cipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher {
        require(key.size == KEY_BYTES) { "Vault keys are $KEY_BYTES bytes" }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(MAGIC)
        }
    }

    private const val BUFFER_BYTES = 64 * 1024
}

internal fun InputStream.readExactly(count: Int): ByteArray {
    val bytes = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = read(bytes, offset, count - offset)
        if (read < 0) throw VaultCryptoException("Unexpected end of data")
        offset += read
    }
    return bytes
}
