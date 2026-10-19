package com.sortr.data.crypto

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyWrapperTest {

    private fun softwareKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `wrapped keys unwrap to the original`() {
        val master = softwareKey()
        val wrapper = AesGcmKeyWrapper { master }
        val dataKey = VaultCipher.newDataKey()

        val wrapped = wrapper.wrap(dataKey)

        assertFalse(wrapped.contentEquals(dataKey))
        assertContentEquals(dataKey, wrapper.unwrap(wrapped))
    }

    @Test
    fun `a different master key cannot unwrap`() {
        val wrapped = AesGcmKeyWrapper(::softwareKey).wrap(VaultCipher.newDataKey())
        val other = softwareKey()

        assertFailsWith<VaultCryptoException> { AesGcmKeyWrapper { other }.unwrap(wrapped) }
    }

    @Test
    fun `tampered or truncated wrapped keys are rejected`() {
        val master = softwareKey()
        val wrapper = AesGcmKeyWrapper { master }
        val wrapped = wrapper.wrap(VaultCipher.newDataKey())

        val tampered = wrapped.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        assertFailsWith<VaultCryptoException> { wrapper.unwrap(tampered) }
        assertFailsWith<VaultCryptoException> { wrapper.unwrap(wrapped.copyOf(12)) }
    }

    @Test
    fun `pin hashes depend on salt and pin`() {
        val hmacKey = KeyGenerator.getInstance("HmacSHA256").generateKey()
        val hasher = HmacPinHasher { hmacKey }
        val salt = ByteArray(16) { it.toByte() }
        val otherSalt = ByteArray(16) { (it + 1).toByte() }

        assertContentEquals(hasher.hash(salt, "1234"), hasher.hash(salt, "1234"))
        assertFalse(hasher.hash(salt, "1234").contentEquals(hasher.hash(salt, "1235")))
        assertFalse(hasher.hash(salt, "1234").contentEquals(hasher.hash(otherSalt, "1234")))
        assertTrue(hasher.hash(salt, "1234").size == 32)
    }
}
