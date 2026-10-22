package com.sortr.data.analysis

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A fingerprint of a file's bytes for finding exact copies. Files up to [FULL_HASH_LIMIT] are
 * hashed whole; bigger ones (mostly videos) by their size and three 1 MB samples from the start,
 * middle and end, which is plenty to tell different videos apart without reading gigabytes.
 */
@Singleton
class ContentFingerprint @Inject constructor(@ApplicationContext private val context: Context) {

    /** Hex SHA-256 prefix, or null if the file can't be read. */
    fun of(uri: Uri, size: Long): String? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(size).array())
                val buffer = ByteBuffer.allocate(SAMPLE_BYTES)
                fun read(from: Long, length: Long) {
                    var position = from
                    val end = from + length
                    while (position < end) {
                        buffer.clear()
                        buffer.limit(minOf(buffer.capacity().toLong(), end - position).toInt())
                        val read = channel.read(buffer, position)
                        if (read <= 0) break
                        digest.update(buffer.array(), 0, read)
                        position += read
                    }
                }
                if (size <= FULL_HASH_LIMIT) {
                    read(0, size)
                } else {
                    read(0, SAMPLE_BYTES.toLong())
                    read(size / 2 - SAMPLE_BYTES / 2, SAMPLE_BYTES.toLong())
                    read(size - SAMPLE_BYTES, SAMPLE_BYTES.toLong())
                }
                digest.digest().take(HASH_BYTES).joinToString("") { String.format(Locale.ROOT, "%02x", it) }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't fingerprint $uri", e)
        null
    }

    private companion object {
        const val TAG = "ContentFingerprint"
        const val FULL_HASH_LIMIT = 32L * 1024 * 1024
        const val SAMPLE_BYTES = 1024 * 1024
        const val HASH_BYTES = 16
    }
}
