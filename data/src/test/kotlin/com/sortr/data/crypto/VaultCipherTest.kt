package com.sortr.data.crypto

import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class VaultCipherTest {

    private fun encrypt(plain: ByteArray, key: ByteArray): ByteArray =
        ByteArrayOutputStream().also { VaultCipher.encrypt(plain, it, key) }.toByteArray()

    private fun decrypt(file: ByteArray, key: ByteArray): ByteArray =
        VaultCipher.decrypt(file.inputStream(), key)

    @Test
    fun `round trip returns the original bytes`() {
        val key = VaultCipher.newDataKey()
        for (size in listOf(0, 1, 15, 16, 17, 65_536, 3 * 1024 * 1024 + 7)) {
            val plain = Random(size).nextBytes(size)
            assertContentEquals(plain, decrypt(encrypt(plain, key), key), "size $size")
        }
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val key = VaultCipher.newDataKey()
        val plain = "a very recognisable photo header ".repeat(50).toByteArray()
        val file = encrypt(plain, key)
        val marker = "recognisable".toByteArray()
        val found = (0..file.size - marker.size).any { i -> marker.indices.all { file[i + it] == marker[it] } }
        assertFalse(found)
    }

    @Test
    fun `encrypting the same bytes twice gives different files`() {
        val key = VaultCipher.newDataKey()
        val plain = Random(1).nextBytes(1_000)
        assertFalse(encrypt(plain, key).contentEquals(encrypt(plain, key)))
    }

    @Test
    fun `wrong key is rejected`() {
        val file = encrypt(Random(2).nextBytes(5_000), VaultCipher.newDataKey())
        assertFailsWith<VaultCryptoException> { decrypt(file, VaultCipher.newDataKey()) }
    }

    @Test
    fun `any changed byte is rejected`() {
        val key = VaultCipher.newDataKey()
        val file = encrypt(Random(3).nextBytes(2_000), key)
        // Header, IV, body and tag.
        for (index in listOf(0, 5, 20, file.size / 2, file.size - 1)) {
            val tampered = file.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertFailsWith<VaultCryptoException>("byte $index") { decrypt(tampered, key) }
        }
    }

    @Test
    fun `truncated file is rejected`() {
        val key = VaultCipher.newDataKey()
        val file = encrypt(Random(4).nextBytes(2_000), key)
        assertFailsWith<VaultCryptoException> { decrypt(file.copyOf(file.size - 1), key) }
        assertFailsWith<VaultCryptoException> { decrypt(file.copyOf(10), key) }
    }

    @Test
    fun `nothing is written when authentication fails`() {
        val key = VaultCipher.newDataKey()
        val file = encrypt(Random(5).nextBytes(200_000), key)
        file[file.size - 1] = (file[file.size - 1].toInt() xor 0x01).toByte()
        val out = ByteArrayOutputStream()

        assertFailsWith<VaultCryptoException> { VaultCipher.decrypt(file.inputStream(), out, key) }
        kotlin.test.assertEquals(0, out.size())
    }
}
