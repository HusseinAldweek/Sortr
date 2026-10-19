package com.sortr.domain.analysis

/**
 * Sharpness score: variance of the Laplacian (a second-derivative edge filter). Sharp photos
 * have many strong edges and a high variance; blurry or out-of-focus ones score low.
 */
object BlurMeter {

    /**
     * Photos scoring below this are flagged as blurry. Calibrated for the 512 px analysis copy;
     * plain subjects (a clear sky, a white wall) can also score low.
     */
    const val BLURRY_THRESHOLD = 60.0

    /** [gray] holds one 0–255 value per pixel, row by row. */
    fun laplacianVariance(gray: IntArray, width: Int, height: Int): Double {
        require(gray.size >= width * height) { "Bad image size" }
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var sumOfSquares = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val laplacian = gray[i - 1] + gray[i + 1] + gray[i - width] + gray[i + width] - 4 * gray[i]
                sum += laplacian
                sumOfSquares += laplacian.toDouble() * laplacian
                count++
            }
        }
        val mean = sum / count
        return sumOfSquares / count - mean * mean
    }

    fun isBlurry(score: Double?): Boolean = score != null && score >= 0 && score < BLURRY_THRESHOLD
}
