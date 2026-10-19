package com.sortr.data.faces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.sortr.domain.model.FaceBox
import com.sortr.domain.model.FaceRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Face thumbnails for the People screens. Only the part of the photo around the face is
 * decoded, which is many times faster than decoding the whole photo, and the result is kept as
 * a small JPEG in the app's private cache so later visits are instant.
 *
 * Crops of photos that go into the vault are deleted right away ([evict]), and [prune] removes
 * crops of faces that are no longer shown, so no vault photo lingers here.
 */
@Singleton
class FaceCropLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File get() = File(context.cacheDir, DIR)

    suspend fun load(face: FaceRef): Bitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(face.photoId, face.faceId)
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.let { return@withContext it.also { file.setLastModified(System.currentTimeMillis()) } }
        }
        val uri = face.photoUri.toUri()
        val crop = try {
            cropRegion(uri, face.box) ?: cropFromFullDecode(uri, face.box)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't crop face ${face.faceId}", e)
            null
        } ?: return@withContext null
        val square = Bitmap.createScaledBitmap(crop, SIZE_PX, SIZE_PX, true).also { if (it !== crop) crop.recycle() }
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            temp.renameTo(file)
        }
        square
    }

    /** Deletes every cached crop of a photo, e.g. when it moves into the vault. */
    suspend fun evict(photoId: Long) = withContext(Dispatchers.IO) {
        File(root, photoId.toString()).deleteRecursively()
    }

    /**
     * Keeps only crops of [shownFaceIds], then trims the cache to [MAX_CACHE_BYTES] by dropping
     * the least recently used crops.
     */
    suspend fun prune(shownFaceIds: Set<Long>) = withContext(Dispatchers.IO) {
        val files = root.listFiles().orEmpty().flatMap { dir -> dir.listFiles().orEmpty().toList() }
        val kept = files.filter { file ->
            val faceId = file.nameWithoutExtension.toLongOrNull()
            (faceId != null && faceId in shownFaceIds && file.extension == "jpg").also { keep -> if (!keep) file.delete() }
        }
        var total = kept.sumOf { it.length() }
        if (total > MAX_CACHE_BYTES) {
            for (file in kept.sortedBy { it.lastModified() }) {
                if (total <= MAX_CACHE_BYTES) break
                total -= file.length()
                file.delete()
            }
        }
        root.listFiles().orEmpty().filter { it.isDirectory && it.list().isNullOrEmpty() }.forEach { it.delete() }
    }

    private fun fileFor(photoId: Long, faceId: Long) = File(File(root, photoId.toString()), "$faceId.jpg")

    /**
     * Decodes just the square around the face. Returns null for images the region decoder can't
     * read or for mirrored EXIF orientations, which then take the slower full-decode path.
     */
    private fun cropRegion(uri: Uri, box: FaceBox): Bitmap? {
        val resolver = context.contentResolver
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: return null
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> 0
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> return null
        }
        val decoder = resolver.openInputStream(uri)?.use { BitmapRegionDecoder.newInstance(it) } ?: return null
        try {
            val rawW = decoder.width
            val rawH = decoder.height
            val shownW = if (degrees % 180 == 0) rawW else rawH
            val shownH = if (degrees % 180 == 0) rawH else rawW
            val shown = squareAround(box, shownW, shownH)
            // The box is relative to the photo as shown; the decoder works on the stored pixels.
            val raw = when (degrees) {
                90 -> Rect(shown.top, rawH - shown.right, shown.bottom, rawH - shown.left)
                180 -> Rect(rawW - shown.right, rawH - shown.bottom, rawW - shown.left, rawH - shown.top)
                270 -> Rect(rawW - shown.bottom, shown.left, rawW - shown.top, shown.right)
                else -> shown
            }
            var sample = 1
            while (raw.width() / (sample * 2) >= SIZE_PX) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val region = decoder.decodeRegion(raw, options) ?: return null
            if (degrees == 0) return region
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(region, 0, 0, region.width, region.height, matrix, true).also {
                if (it !== region) region.recycle()
            }
        } finally {
            decoder.recycle()
        }
    }

    /** Slower fallback: decode the whole photo, already rotated, just large enough, then crop. */
    private fun cropFromFullDecode(uri: Uri, box: FaceBox): Bitmap? {
        val faceFraction = (max(box.width, box.height) * (1 + 2 * MARGIN)).coerceIn(0.05f, 1f)
        val decodePx = (SIZE_PX / faceFraction).roundToInt().coerceIn(SIZE_PX, MAX_DECODE_PX)
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > decodePx) {
                val scale = decodePx.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val square = squareAround(box, bitmap.width, bitmap.height)
        return Bitmap.createBitmap(bitmap, square.left, square.top, square.width(), square.height()).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private companion object {
        const val TAG = "FaceCrop"
        const val DIR = "faces"
        const val SIZE_PX = 320
        const val JPEG_QUALITY = 85
        const val MARGIN = 0.3f
        const val MAX_DECODE_PX = 2048
        const val MAX_CACHE_BYTES = 100L * 1024 * 1024

        /** The face plus [MARGIN] on each side, as a square kept inside the photo. */
        fun squareAround(box: FaceBox, w: Int, h: Int): Rect {
            val faceSide = max(box.width * w, box.height * h)
            val side = (faceSide * (1 + 2 * MARGIN)).roundToInt().coerceIn(1, min(w, h))
            val cx = (box.left + box.right) / 2 * w
            val cy = (box.top + box.bottom) / 2 * h
            val left = (cx - side / 2f).roundToInt().coerceIn(0, w - side)
            val top = (cy - side / 2f).roundToInt().coerceIn(0, h - side)
            return Rect(left, top, left + side, top + side)
        }
    }
}
