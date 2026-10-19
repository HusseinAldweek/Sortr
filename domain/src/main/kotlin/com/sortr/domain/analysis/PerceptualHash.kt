package com.sortr.domain.analysis

/**
 * Difference hash (dHash): shrink the image to 9×8 grey levels and record, for each row,
 * whether brightness rises from one cell to the next. The 64 bits survive resizing,
 * recompression and small edits, so near-identical photos get hashes a few bits apart.
 */
object PerceptualHash {

    private const val COLUMNS = 9
    private const val ROWS = 8

    /** [gray] holds one 0–255 value per pixel, row by row. */
    fun dHash(gray: IntArray, width: Int, height: Int): Long {
        require(width > 0 && height > 0 && gray.size >= width * height) { "Bad image size" }
        val cells = shrink(gray, width, height, COLUMNS, ROWS)
        var hash = 0L
        var bit = 0
        for (y in 0 until ROWS) {
            for (x in 0 until COLUMNS - 1) {
                if (cells[y * COLUMNS + x] < cells[y * COLUMNS + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** Number of differing bits: 0 for identical hashes, up to 64. */
    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Area-average downscale to [targetW]×[targetH]. */
    internal fun shrink(gray: IntArray, width: Int, height: Int, targetW: Int, targetH: Int): DoubleArray {
        val out = DoubleArray(targetW * targetH)
        for (ty in 0 until targetH) {
            val y0 = ty * height / targetH
            val y1 = maxOf(y0 + 1, (ty + 1) * height / targetH)
            for (tx in 0 until targetW) {
                val x0 = tx * width / targetW
                val x1 = maxOf(x0 + 1, (tx + 1) * width / targetW)
                var sum = 0L
                for (y in y0 until minOf(y1, height)) {
                    val row = y * width
                    for (x in x0 until minOf(x1, width)) sum += gray[row + x]
                }
                val count = (minOf(y1, height) - y0) * (minOf(x1, width) - x0)
                out[ty * targetW + tx] = if (count > 0) sum.toDouble() / count else 0.0
            }
        }
        return out
    }
}
