package com.sortr.data.faces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.sortr.data.db.BOX_SCALE
import com.sortr.domain.faces.FaceAlignment
import com.sortr.domain.faces.FaceQuality
import com.sortr.domain.faces.FaceRules
import com.sortr.domain.faces.PixelBox
import com.sortr.domain.faces.Point
import com.sortr.domain.faces.Similarity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** A face found in a photo, ready to be grouped. Box units are 1/[BOX_SCALE] of the photo. */
class DetectedFace(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val embedding: FloatArray,
    val quality: FaceQuality,
)

/**
 * Photo → faces: decode a downscaled copy, detect faces with ML Kit's bundled (offline)
 * detector, drop faces that are too small or turned away, line each one up by its eyes, and
 * embed it with MobileFaceNet.
 */
@Singleton
class FaceAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val model: FaceModel,
) {
    private val detector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setMinFaceSize(MIN_FACE_FRACTION)
                .build(),
        )
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /**
     * Blocking; call off the main thread. A photo that can't be read or analysed yields no
     * faces, but a broken model throws so the photo isn't wrongly marked as scanned.
     */
    fun analyze(uri: Uri): List<DetectedFace> {
        val embedder = model.embedder()
        val bitmap = decode(uri) ?: return emptyList()
        try {
            val faces = try {
                Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
            } catch (e: Exception) {
                Log.w(TAG, "Face detection failed for $uri", e)
                return emptyList()
            }
            val boxes = faces.map { PixelBox(it.boundingBox.left, it.boundingBox.top, it.boundingBox.right, it.boundingBox.bottom) }
            return FaceRules.distinctFaces(boxes).mapNotNull { analyzeFace(bitmap, faces[it], embedder) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun analyzeFace(bitmap: Bitmap, face: Face, embedder: FaceEmbedder): DetectedFace? {
        val box = face.boundingBox
        val quality = FaceRules.classify(
            widthPx = box.width(),
            heightPx = box.height(),
            yaw = face.headEulerAngleY,
            pitch = face.headEulerAngleX,
            roll = face.headEulerAngleZ,
        ) ?: return null

        val size = embedder.inputSize
        val eyes = listOfNotNull(
            face.getLandmark(FaceLandmark.LEFT_EYE)?.position,
            face.getLandmark(FaceLandmark.RIGHT_EYE)?.position,
        ).sortedBy { it.x }
        val transform = if (eyes.size == 2 && eyes[1].x - eyes[0].x > 1f) {
            FaceAlignment.eyesToTemplate(Point(eyes[0].x, eyes[0].y), Point(eyes[1].x, eyes[1].y), size)
        } else {
            FaceAlignment.boxToCrop(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(), size)
        }

        val crop = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val embedding = try {
            Canvas(crop).apply {
                drawColor(Color.BLACK)
                drawBitmap(bitmap, transform.toMatrix(), paint)
            }
            embedder.embed(crop)
        } finally {
            crop.recycle()
        }

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        fun scaleX(x: Int) = (x / w * BOX_SCALE).roundToInt().coerceIn(0, BOX_SCALE)
        fun scaleY(y: Int) = (y / h * BOX_SCALE).roundToInt().coerceIn(0, BOX_SCALE)
        return DetectedFace(scaleX(box.left), scaleY(box.top), scaleX(box.right), scaleY(box.bottom), embedding, quality)
    }

    /** A software ARGB copy at most [MAX_SIDE] pixels on its longest side, with EXIF rotation applied. */
    private fun decode(uri: Uri): Bitmap? = try {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
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

    private fun Similarity.toMatrix() = Matrix().apply { setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)) }

    private companion object {
        const val TAG = "FaceAnalyzer"
        /** Longest side of the copy faces are detected in. */
        const val MAX_SIDE = 1280
        /** Faces below 80 px in a 1280 px image are dropped anyway; don't ask ML Kit for them. */
        const val MIN_FACE_FRACTION = 0.05f
    }
}
