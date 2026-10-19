package com.sortr.domain.analysis

import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Small synthetic greyscale images for analysis tests. */
internal class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {

    fun map(transform: (Int) -> Int) = GrayImage(width, height, IntArray(pixels.size) { transform(pixels[it]).coerceIn(0, 255) })

    /** Nearest-neighbour resize, like a re-saved smaller copy. */
    fun resized(newWidth: Int, newHeight: Int) = GrayImage(
        newWidth,
        newHeight,
        IntArray(newWidth * newHeight) { i ->
            val x = (i % newWidth) * width / newWidth
            val y = (i / newWidth) * height / newHeight
            pixels[y * width + x]
        },
    )

    /** Box blur of the given radius, a stand-in for an out-of-focus shot. */
    fun blurred(radius: Int): GrayImage {
        val out = IntArray(pixels.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0
            var count = 0
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until width && ny in 0 until height) {
                    sum += pixels[ny * width + nx]
                    count++
                }
            }
            out[y * width + x] = sum / count
        }
        return GrayImage(width, height, out)
    }

    companion object {
        /** A "photo": smooth shapes plus fine texture, different for each seed. */
        fun scene(seed: Int, width: Int = 160, height: Int = 120): GrayImage {
            val random = Random(seed)
            val fx = random.nextDouble(0.02, 0.08)
            val fy = random.nextDouble(0.02, 0.08)
            val phase = random.nextDouble(0.0, 6.0)
            val pixels = IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                val smooth = 128 + 80 * sin(x * fx + phase) * sin(y * fy + phase / 2)
                val edges = if ((x / 8 + y / 8 + seed) % 5 == 0) 40 else 0
                val texture = random.nextInt(-20, 21)
                (smooth + edges + texture).roundToInt().coerceIn(0, 255)
            }
            return GrayImage(width, height, pixels)
        }

        fun flat(value: Int, width: Int = 64, height: Int = 64) = GrayImage(width, height, IntArray(width * height) { value })
    }
}
