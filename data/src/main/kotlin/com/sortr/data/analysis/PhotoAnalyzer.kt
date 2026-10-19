package com.sortr.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.sortr.domain.analysis.BlurMeter
import com.sortr.domain.analysis.PerceptualHash
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

class PhotoFingerprint(
    val hash: Long,
    val blurScore: Double,
    /** When the photo says it was taken (EXIF), in epoch millis; null if it doesn't say. */
    val exifDateTaken: Long?,
    /** Full size of the photo, before it was shrunk for analysis. */
    val width: Int,
    val height: Int,
)

/** Photo → perceptual hash and sharpness score, from a small greyscale copy. Fully on-device. */
@Singleton
class PhotoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Null when the photo can't be decoded. */
    fun analyze(uri: Uri): PhotoFingerprint? {
        var fullWidth = 0
        var fullHeight = 0
        val bitmap = decode(uri) { w, h ->
            fullWidth = w
            fullHeight = h
        } ?: return null
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val gray = IntArray(pixels.size) { i ->
                val p = pixels[i]
                (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            }
            PhotoFingerprint(
                hash = PerceptualHash.dHash(gray, width, height),
                blurScore = BlurMeter.laplacianVariance(gray, width, height),
                exifDateTaken = exifDateTaken(uri),
                width = fullWidth,
                height = fullHeight,
            )
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * MediaStore leaves the capture date empty when the photo's EXIF date has no time zone,
     * which is common. The date is then read here, as local time unless an offset is given.
     */
    private fun exifDateTaken(uri: Uri): Long? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val local = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            local?.trim()?.takeIf { it.length >= 19 }?.let { text ->
                val dateTime = LocalDateTime.parse(text.substring(0, 19), EXIF_DATE)
                val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                    ?.let { runCatching { ZoneOffset.of(it.trim()) }.getOrNull() }
                val instant = if (offset != null) dateTime.toInstant(offset) else dateTime.atZone(ZoneId.systemDefault()).toInstant()
                instant.toEpochMilli().takeIf { dateTime.year >= MIN_YEAR }
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun decode(uri: Uri, onFullSize: (Int, Int) -> Unit): Bitmap? = try {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            onFullSize(info.size.width, info.size.height)
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't decode $uri", e)
        null
    }

    private companion object {
        const val TAG = "PhotoAnalyzer"

        /** BlurMeter's threshold is calibrated for copies of this size. */
        const val MAX_SIDE = 512

        /** Cameras with an unset clock write 0000:00:00 or 1970; those dates are ignored. */
        const val MIN_YEAR = 1990
        val EXIF_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}
