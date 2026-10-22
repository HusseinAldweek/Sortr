package com.sortr.data.vault

import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale

/** What can be learned from a shared photo's bytes alone: size, type and capture date. */
internal data class SharedPhotoInfo(
    val width: Int,
    val height: Int,
    val mimeType: String,
    val extension: String,
    val dateTaken: Long?,
) {
    companion object {
        fun read(bytes: ByteArray): SharedPhotoInfo {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val mime = bounds.outMimeType ?: "image/jpeg"
            val date = runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { text ->
                    // EXIF dates are always written with ASCII digits in this exact shape.
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).parse(text)?.time
                }
            }.getOrNull()
            return SharedPhotoInfo(
                width = bounds.outWidth.coerceAtLeast(0),
                height = bounds.outHeight.coerceAtLeast(0),
                mimeType = mime,
                extension = mime.substringAfter('/', "jpg").let { if (it == "jpeg") "jpg" else it },
                dateTaken = date,
            )
        }
    }
}
