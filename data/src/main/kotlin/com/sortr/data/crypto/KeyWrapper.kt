package com.sortr.data.crypto

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Protects per-file data keys with a master key that never leaves secure storage. */
interface KeyWrapper {
    fun wrap(key: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * Wraps keys with AES-GCM under [masterKey]. In the app the master key lives in the Android
 * Keystore, which generates the IV itself; tests pass a plain software key.
 *
 * Wrapped layout: IV (12 bytes) | encrypted key | tag.
 */
class AesGcmKeyWrapper(private val masterKey: () -> SecretKey) : KeyWrapper {

    override fun wrap(key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected IV size ${iv.size}" }
        return iv + cipher.doFinal(key)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (wrapped.size <= IV_BYTES) throw VaultCryptoException("Wrapped key too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            masterKey(),
            GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES),
        )
        return try {
            cipher.doFinal(wrapped, IV_BYTES, wrapped.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw VaultCryptoException("Couldn't unwrap vault key", e)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
