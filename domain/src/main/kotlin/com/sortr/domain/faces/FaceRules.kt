package com.sortr.domain.faces

import kotlin.math.abs

/** A face box in pixels. */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val area: Long get() = (right - left).coerceAtLeast(0).toLong() * (bottom - top).coerceAtLeast(0)

    fun intersectionArea(other: PixelBox): Long {
        val w = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0)
        val h = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0)
        return w.toLong() * h
    }
}

enum class FaceQuality {
    /** Small or turned away: may join an existing person, but never starts a new one. */
    WEAK,
    GOOD,
}

/** Which detected faces are worth recognising. Sizes are in pixels of the image the face was found in. */
object FaceRules {
    const val MIN_FACE_PX = 80
    const val GOOD_FACE_PX = 112

    private const val MAX_YAW = 40f
    private const val MAX_PITCH = 30f
    private const val MAX_ROLL = 45f
    private const val GOOD_YAW = 20f
    private const val GOOD_PITCH = 20f

    /** Overlap, as a share of the smaller box, above which two boxes are taken to be one face. */
    const val DUPLICATE_OVERLAP = 0.5f

    /**
     * The detector sometimes reports one face several times with nested boxes. Returns the
     * indices of the boxes to keep: largest first, dropping any box that mostly overlaps one
     * already kept.
     */
    fun distinctFaces(boxes: List<PixelBox>, maxOverlap: Float = DUPLICATE_OVERLAP): List<Int> {
        val kept = mutableListOf<Int>()
        for (index in boxes.indices.sortedByDescending { boxes[it].area }) {
            val box = boxes[index]
            val duplicate = kept.any { keptIndex ->
                val other = boxes[keptIndex]
                val smaller = minOf(box.area, other.area)
                smaller > 0 && box.intersectionArea(other).toFloat() / smaller > maxOverlap
            }
            if (!duplicate && box.area > 0) kept += index
        }
        return kept.sorted()
    }

    /** Null means skip the face: too small, or the head is turned too far. */
    fun classify(widthPx: Int, heightPx: Int, yaw: Float, pitch: Float, roll: Float): FaceQuality? {
        if (minOf(widthPx, heightPx) < MIN_FACE_PX) return null
        if (abs(yaw) > MAX_YAW || abs(pitch) > MAX_PITCH || abs(roll) > MAX_ROLL) return null
        val good = minOf(widthPx, heightPx) >= GOOD_FACE_PX && abs(yaw) <= GOOD_YAW && abs(pitch) <= GOOD_PITCH
        return if (good) FaceQuality.GOOD else FaceQuality.WEAK
    }
}
