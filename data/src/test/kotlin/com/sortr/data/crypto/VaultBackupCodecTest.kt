package com.sortr.data.crypto

import com.sortr.domain.model.VaultBackupException
import com.sortr.domain.model.VaultBackupException.Reason
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VaultBackupCodecTest {

    // Low iteration count keeps the tests fast; the format stores whatever count was used.
    private val iterations = 2_000
    private val password = "correct horse battery".toCharArray()

    private data class Item(val meta: BackupItemMeta, val photo: ByteArray, val thumbnail: ByteArray?)

    private fun item(n: Int, withThumbnail: Boolean = true) = Item(
        meta = BackupItemMeta(
            originalName = "IMG_$n.jpg",
            mimeType = "image/jpeg",
            originalSize = 1_000L + n,
            dateTaken = 1_700_000_000_000L + n,
            addedAt = 1_800_000_000_000L + n,
            width = 4000,
            height = 3000,
            sha256 = if (n % 2 == 0) "ab".repeat(32) else null,
        ),
        photo = Random(n).nextBytes(10_000 + n),
        thumbnail = if (withThumbnail) Random(n + 100).nextBytes(500) else null,
    )

    private fun backup(items: List<Item>, pass: CharArray = password): ByteArray {
        val out = ByteArrayOutputStream()
        VaultBackupWriter(out, pass.copyOf(), items.size, iterations).apply {
            items.forEach { writeItem(it.meta, it.photo, it.thumbnail) }
            finish()
        }
        return out.toByteArray()
    }

    private fun restore(file: ByteArray, pass: CharArray = password): List<Item> {
        val items = mutableListOf<Item>()
        VaultBackupReader(file.inputStream(), pass.copyOf()).readItems { meta, photo, thumb ->
            items += Item(meta, photo, thumb)
        }
        return items
    }

    private fun reasonOf(block: () -> Unit): Reason = assertFailsWith<VaultBackupException> { block() }.reason

    @Test
    fun `backup round trip restores every item exactly`() {
        val items = listOf(item(1), item(2, withThumbnail = false), item(3))

        val restored = restore(backup(items))

        assertEquals(items.size, restored.size)
        items.zip(restored).forEach { (expected, actual) ->
            assertEquals(expected.meta, actual.meta)
            assertContentEquals(expected.photo, actual.photo)
            if (expected.thumbnail == null) assertNull(actual.thumbnail) else assertContentEquals(expected.thumbnail, actual.thumbnail)
        }
    }

    @Test
    fun `an empty vault makes a valid backup`() {
        val file = backup(emptyList())
        assertEquals(0, VaultBackupReader(file.inputStream(), password.copyOf()).itemCount)
        assertEquals(emptyList(), restore(file))
    }

    @Test
    fun `reader reports the item count before reading`() {
        val file = backup(listOf(item(1), item(2)))
        assertEquals(2, VaultBackupReader(file.inputStream(), password.copyOf()).itemCount)
    }

    @Test
    fun `wrong password is reported as such`() {
        val file = backup(listOf(item(1)))
        assertEquals(Reason.WRONG_PASSWORD, reasonOf { restore(file, "wrong password".toCharArray()) })
    }

    @Test
    fun `a file that is not a backup is recognised`() {
        assertEquals(Reason.NOT_A_BACKUP, reasonOf { restore("hello, this is a text file".toByteArray()) })
        assertEquals(Reason.NOT_A_BACKUP, reasonOf { restore(ByteArray(0)) })
    }

    @Test
    fun `changed bytes after the first chunk are reported as damage`() {
        val file = backup(listOf(item(1), item(2)))
        val tampered = file.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertEquals(Reason.DAMAGED, reasonOf { restore(tampered) })
    }

    @Test
    fun `a cut short file is reported as damage`() {
        val file = backup(listOf(item(1), item(2)))
        assertEquals(Reason.DAMAGED, reasonOf { restore(file.copyOf(file.size - 40)) })
    }

    @Test
    fun `a newer format version is refused`() {
        val file = backup(listOf(item(1)))
        file[VaultBackupCodec.MAGIC.size] = 2
        assertEquals(Reason.UNSUPPORTED_VERSION, reasonOf { restore(file) })
    }
}
