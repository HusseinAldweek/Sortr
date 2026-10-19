package com.sortr.data.crypto

import javax.crypto.Mac
import javax.crypto.SecretKey

/** Turns a PIN into a stored verifier. */
fun interface PinHasher {
    fun hash(salt: ByteArray, pin: String): ByteArray
}

/**
 * HMAC-SHA256 of salt and PIN under [key]. In the app the key is held by the Android Keystore,
 * so a copied verifier can't be brute-forced off the phone, even though PINs are short.
 */
class HmacPinHasher(private val key: () -> SecretKey) : PinHasher {
    override fun hash(salt: ByteArray, pin: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(key())
            update(salt)
            doFinal(pin.toByteArray(Charsets.UTF_8))
        }
}
