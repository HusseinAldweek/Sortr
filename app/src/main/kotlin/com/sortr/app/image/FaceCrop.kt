package com.sortr.app.image

import android.graphics.Bitmap
import androidx.core.net.toUri
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import com.sortr.domain.model.FaceBox
import com.sortr.domain.model.FaceRef
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Crops a decoded photo to a square around one face, with some room around it. */
class FaceCropTransformation(
    private val box: FaceBox,
    private val margin: Float = 0.3f,
) : Transformation() {

    override val cacheKey: String = "faceCrop(${box.left},${box.top},${box.right},${box.bottom},$margin)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val faceSide = max(box.width * w, box.height * h)
        val side = (faceSide * (1 + 2 * margin)).roundToInt().coerceIn(1, min(w, h))
        val cx = (box.left + box.right) / 2 * w
        val cy = (box.top + box.bottom) / 2 * h
        val left = (cx - side / 2f).roundToInt().coerceIn(0, w - side)
        val top = (cy - side / 2f).roundToInt().coerceIn(0, h - side)
        return Bitmap.createBitmap(input, left, top, side, side)
    }
}

/**
 * Request for a face thumbnail of about [sizePx] pixels. The photo is decoded only as large as
 * needed for the face itself to reach that size.
 */
fun faceImageRequest(context: PlatformContext, face: FaceRef, sizePx: Int): ImageRequest {
    val faceFraction = (max(face.box.width, face.box.height) * 1.6f).coerceIn(0.05f, 1f)
    val decodePx = (sizePx / faceFraction).roundToInt().coerceIn(sizePx, MAX_DECODE_PX)
    return ImageRequest.Builder(context)
        .data(face.photoUri.toUri())
        .size(decodePx)
        .transformations(FaceCropTransformation(face.box))
        .memoryCacheKey("face:${face.faceId}:$sizePx")
        .build()
}

private const val MAX_DECODE_PX = 2048
