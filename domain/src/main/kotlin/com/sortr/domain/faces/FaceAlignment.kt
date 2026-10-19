package com.sortr.domain.faces

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

data class Point(val x: Float, val y: Float)

/**
 * Similarity transform (scale, rotation, translation):
 * x' = a·x − b·y + tx,  y' = b·x + a·y + ty.
 * Laid out so it maps straight onto an android.graphics.Matrix: [a, −b, tx, b, a, ty, 0, 0, 1].
 */
data class Similarity(val a: Float, val b: Float, val tx: Float, val ty: Float) {
    fun map(p: Point): Point = Point(a * p.x - b * p.y + tx, b * p.x + a * p.y + ty)
}

/**
 * Lines faces up the way face-recognition models were trained: eyes level and at fixed
 * positions in the crop. The positions are the standard ArcFace template for 112×112 crops,
 * which leaves margin for the forehead and chin.
 */
object FaceAlignment {
    const val TEMPLATE_SIZE = 112f
    val TEMPLATE_LEFT_EYE = Point(38.2946f, 51.6963f)
    val TEMPLATE_RIGHT_EYE = Point(73.5318f, 51.5014f)

    /**
     * Maps the two eye centres onto the template eye positions in a square crop of [outputSize]
     * pixels. [leftEye] is the eye further left in the image.
     */
    fun eyesToTemplate(leftEye: Point, rightEye: Point, outputSize: Int): Similarity {
        val k = outputSize / TEMPLATE_SIZE
        val dstLeft = Point(TEMPLATE_LEFT_EYE.x * k, TEMPLATE_LEFT_EYE.y * k)
        val dstRight = Point(TEMPLATE_RIGHT_EYE.x * k, TEMPLATE_RIGHT_EYE.y * k)

        val srcDx = rightEye.x - leftEye.x
        val srcDy = rightEye.y - leftEye.y
        val dstDx = dstRight.x - dstLeft.x
        val dstDy = dstRight.y - dstLeft.y
        val srcLength = hypot(srcDx, srcDy)
        require(srcLength > 0f) { "Eye positions coincide" }

        val scale = hypot(dstDx, dstDy) / srcLength
        val angle = atan2(dstDy, dstDx) - atan2(srcDy, srcDx)
        val a = scale * cos(angle)
        val b = scale * sin(angle)
        // Translate so the left eye lands on its template position.
        val tx = dstLeft.x - (a * leftEye.x - b * leftEye.y)
        val ty = dstLeft.y - (b * leftEye.x + a * leftEye.y)
        return Similarity(a, b, tx, ty)
    }

    /**
     * Fallback when the eyes weren't found: a square around the face box, enlarged by [margin]
     * on each side, scaled to fill the crop.
     */
    fun boxToCrop(left: Float, top: Float, right: Float, bottom: Float, outputSize: Int, margin: Float = 0.15f): Similarity {
        val side = max(right - left, bottom - top) * (1 + 2 * margin)
        require(side > 0f) { "Empty face box" }
        val scale = outputSize / side
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        return Similarity(scale, 0f, outputSize / 2f - scale * cx, outputSize / 2f - scale * cy)
    }
}
