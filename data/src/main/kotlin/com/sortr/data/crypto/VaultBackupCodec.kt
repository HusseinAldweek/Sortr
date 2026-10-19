package com.sortr.data.crypto

import com.sortr.domain.model.VaultBackupException
import com.sortr.domain.model.VaultBackupException.Reason
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** What a backup stores about each photo, besides the image bytes. */
data class BackupItemMeta(
    val originalName: String,
    val mimeType: String,
    val originalSize: Long,
    val dateTaken: Long,
    val addedAt: Long,
    val width: Int,
    val height: Int,
    val sha256: String?,
)

class BackupEntry(val meta: BackupItemMeta, val photo: ByteArray, val thumbnail: ByteArray?)

/**
 * Vault backup file format, version 1.
 *
 * The Keystore master key can't leave the phone, so a backup is re-encrypted with a key derived
 * from a password (PBKDF2-HMAC-SHA256).
 *
 * Header (plain): `SORTRBAK` | version u8 | kdf u8 | iterations i32 | salt (16 bytes).
 * Then chunks, each: IV (12 bytes) | length i32 | AES-256-GCM ciphertext of (type u8 + payload).
 * Every chunk is authenticated together with the header and its position, so chunks can't be
 * altered, reordered or swapped between files. The first chunk is a manifest holding the item
 * count; each item is a metadata, photo and thumbnail chunk; an end chunk repeats the count so
 * a cut-short file is detected.
 */
object VaultBackupCodec {
    const val DEFAULT_ITERATIONS = 600_000
    internal const val MIN_ITERATIONS = 1_000
    internal const val MAX_ITERATIONS = 10_000_000

    internal val MAGIC = "SORTRBAK".toByteArray(Charsets.US_ASCII)
    internal const val VERSION: Byte = 1
    internal const val KDF_PBKDF2_SHA256: Byte = 1
    internal const val SALT_BYTES = 16
    internal const val IV_BYTES = 12
    internal const val TAG_BITS = 128
    internal const val MAX_CHUNK_BYTES = 512 * 1024 * 1024

    internal const val TYPE_MANIFEST: Byte = 0
    internal const val TYPE_META: Byte = 1
    internal const val TYPE_PHOTO: Byte = 2
    internal const val TYPE_THUMBNAIL: Byte = 3
    internal const val TYPE_END: Byte = 9

    internal fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

class VaultBackupWriter(
    output: OutputStream,
    password: CharArray,
    private val itemCount: Int,
    iterations: Int = VaultBackupCodec.DEFAULT_ITERATIONS,
) {
    private val out = DataOutputStream(BufferedOutputStream(output))
    private val header: ByteArray
    private val key: SecretKey
    private val random = SecureRandom()
    private var chunkIndex = 0L
    private var written = 0

    init {
        val salt = ByteArray(VaultBackupCodec.SALT_BYTES).also(random::nextBytes)
        header = ByteArrayOutputStream().apply {
            DataOutputStream(this).run {
                write(VaultBackupCodec.MAGIC)
                writeByte(VaultBackupCodec.VERSION.toInt())
                writeByte(VaultBackupCodec.KDF_PBKDF2_SHA256.toInt())
                writeInt(iterations)
                write(salt)
            }
        }.toByteArray()
        key = VaultBackupCodec.deriveKey(password, salt, iterations)
        out.write(header)
        writeChunk(VaultBackupCodec.TYPE_MANIFEST, ByteBuffer.allocate(4).putInt(itemCount).array())
    }

    fun writeItem(meta: BackupItemMeta, photo: ByteArray, thumbnail: ByteArray?) {
        check(written < itemCount) { "More items than announced" }
        val metaBytes = ByteArrayOutputStream().apply {
            DataOutputStream(this).run {
                writeUTF(meta.originalName)
                writeUTF(meta.mimeType)
                writeLong(meta.originalSize)
                writeLong(meta.dateTaken)
                writeLong(meta.addedAt)
                writeInt(meta.width)
                writeInt(meta.height)
                writeUTF(meta.sha256.orEmpty())
            }
        }.toByteArray()
        writeChunk(VaultBackupCodec.TYPE_META, metaBytes)
        writeChunk(VaultBackupCodec.TYPE_PHOTO, photo)
        writeChunk(VaultBackupCodec.TYPE_THUMBNAIL, thumbnail ?: ByteArray(0))
        written++
    }

    fun finish() {
        check(written == itemCount) { "Wrote $written of $itemCount items" }
        writeChunk(VaultBackupCodec.TYPE_END, ByteBuffer.allocate(4).putInt(written).array())
        out.flush()
    }

    private fun writeChunk(type: Byte, payload: ByteArray) {
        val iv = ByteArray(VaultBackupCodec.IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(VaultBackupCodec.TAG_BITS, iv))
            updateAAD(chunkAad(header, chunkIndex))
        }
        val first = cipher.update(byteArrayOf(type)) ?: ByteArray(0)
        val rest = cipher.doFinal(payload)
        out.write(iv)
        out.writeInt(first.size + rest.size)
        out.write(first)
        out.write(rest)
        chunkIndex++
    }
}

class VaultBackupReader(input: InputStream, password: CharArray) {
    private val data = DataInputStream(BufferedInputStream(input))
    private val header: ByteArray
    private val key: SecretKey
    private var chunkIndex = 0L

    /** Number of items the backup says it holds. */
    val itemCount: Int

    init {
        val magic = readOrNull(VaultBackupCodec.MAGIC.size)
        if (magic == null || !magic.contentEquals(VaultBackupCodec.MAGIC)) {
            throw VaultBackupException(Reason.NOT_A_BACKUP)
        }
        val version = data.readByteOr(Reason.NOT_A_BACKUP)
        if (version != VaultBackupCodec.VERSION) throw VaultBackupException(Reason.UNSUPPORTED_VERSION)
        val kdf = data.readByteOr(Reason.NOT_A_BACKUP)
        if (kdf != VaultBackupCodec.KDF_PBKDF2_SHA256) throw VaultBackupException(Reason.UNSUPPORTED_VERSION)
        val iterations = try { data.readInt() } catch (e: EOFException) { throw VaultBackupException(Reason.NOT_A_BACKUP, e) }
        if (iterations !in VaultBackupCodec.MIN_ITERATIONS..VaultBackupCodec.MAX_ITERATIONS) {
            throw VaultBackupException(Reason.NOT_A_BACKUP)
        }
        val salt = readOrNull(VaultBackupCodec.SALT_BYTES) ?: throw VaultBackupException(Reason.NOT_A_BACKUP)
        header = ByteArrayOutputStream().apply {
            DataOutputStream(this).run {
                write(magic)
                writeByte(version.toInt())
                writeByte(kdf.toInt())
                writeInt(iterations)
                write(salt)
            }
        }.toByteArray()
        key = VaultBackupCodec.deriveKey(password, salt, iterations)

        val (type, payload) = readChunk()
        if (type != VaultBackupCodec.TYPE_MANIFEST || payload.size != 4) throw VaultBackupException(Reason.DAMAGED)
        itemCount = ByteBuffer.wrap(payload).int
    }

    private var itemsRead = 0
    private var finished = false

    /**
     * The next photo, or null once the end of the backup has been reached and checked.
     * Throws [VaultBackupException] if the file is damaged or cut short.
     */
    fun next(): BackupEntry? {
        if (finished) return null
        val (type, payload) = readChunk()
        return when (type) {
            VaultBackupCodec.TYPE_META -> {
                val meta = parseMeta(payload)
                val photo = expect(VaultBackupCodec.TYPE_PHOTO)
                val thumbnail = expect(VaultBackupCodec.TYPE_THUMBNAIL).takeIf { it.isNotEmpty() }
                itemsRead++
                BackupEntry(meta, photo, thumbnail)
            }
            VaultBackupCodec.TYPE_END -> {
                val count = ByteBuffer.wrap(payload).int
                if (count != itemsRead || count != itemCount) throw VaultBackupException(Reason.DAMAGED)
                finished = true
                null
            }
            else -> throw VaultBackupException(Reason.DAMAGED)
        }
    }

    /** Calls [onItem] for each photo in order, then checks the file ends properly. */
    fun readItems(onItem: (meta: BackupItemMeta, photo: ByteArray, thumbnail: ByteArray?) -> Unit) {
        while (true) {
            val entry = next() ?: return
            onItem(entry.meta, entry.photo, entry.thumbnail)
        }
    }

    private fun expect(type: Byte): ByteArray {
        val (actual, payload) = readChunk()
        if (actual != type) throw VaultBackupException(Reason.DAMAGED)
        return payload
    }

    private fun readChunk(): Pair<Byte, ByteArray> {
        val iv = readOrNull(VaultBackupCodec.IV_BYTES) ?: throw VaultBackupException(Reason.DAMAGED)
        val length = try { data.readInt() } catch (e: EOFException) { throw VaultBackupException(Reason.DAMAGED, e) }
        if (length < 1 + VaultBackupCodec.TAG_BITS / 8 || length > VaultBackupCodec.MAX_CHUNK_BYTES) {
            throw VaultBackupException(Reason.DAMAGED)
        }
        val ciphertext = readOrNull(length) ?: throw VaultBackupException(Reason.DAMAGED)
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(VaultBackupCodec.TAG_BITS, iv))
                updateAAD(chunkAad(header, chunkIndex))
                doFinal(ciphertext)
            }
        } catch (e: GeneralSecurityException) {
            // The very first chunk failing almost always means the password is wrong.
            throw VaultBackupException(if (chunkIndex == 0L) Reason.WRONG_PASSWORD else Reason.DAMAGED, e)
        }
        chunkIndex++
        return plain[0] to plain.copyOfRange(1, plain.size)
    }

    private fun parseMeta(payload: ByteArray): BackupItemMeta = try {
        DataInputStream(ByteArrayInputStream(payload)).run {
            BackupItemMeta(
                originalName = readUTF(),
                mimeType = readUTF(),
                originalSize = readLong(),
                dateTaken = readLong(),
                addedAt = readLong(),
                width = readInt(),
                height = readInt(),
                sha256 = readUTF().ifEmpty { null },
            )
        }
    } catch (e: java.io.IOException) {
        throw VaultBackupException(Reason.DAMAGED, e)
    }

    private fun readOrNull(count: Int): ByteArray? {
        val bytes = ByteArray(count)
        return try {
            data.readFully(bytes)
            bytes
        } catch (e: EOFException) {
            null
        }
    }

    private fun DataInputStream.readByteOr(reason: Reason): Byte =
        try { readByte() } catch (e: EOFException) { throw VaultBackupException(reason, e) }
}

private fun chunkAad(header: ByteArray, index: Long): ByteArray =
    ByteBuffer.allocate(header.size + 8).put(header).putLong(index).array()
