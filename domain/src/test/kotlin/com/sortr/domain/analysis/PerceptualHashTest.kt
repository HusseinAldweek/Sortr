package com.sortr.domain.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerceptualHashTest {

    private fun hash(image: GrayImage) = PerceptualHash.dHash(image.pixels, image.width, image.height)

    @Test
    fun `identical images have identical hashes`() {
        val image = GrayImage.scene(1)
        assertEquals(hash(image), hash(GrayImage.scene(1)))
    }

    @Test
    fun `a brighter copy hashes the same or nearly`() {
        val image = GrayImage.scene(2)
        val brighter = image.map { it + 25 }
        assertTrue(PerceptualHash.distance(hash(image), hash(brighter)) <= 3)
    }

    @Test
    fun `a resized copy is within the duplicate distance`() {
        val image = GrayImage.scene(3)
        val smaller = image.resized(80, 60)
        assertTrue(PerceptualHash.distance(hash(image), hash(smaller)) <= DuplicateFinder.DEFAULT_MAX_DISTANCE)
    }

    @Test
    fun `different scenes are far apart`() {
        val distance = PerceptualHash.distance(hash(GrayImage.scene(4)), hash(GrayImage.scene(5)))
        assertTrue(distance > DuplicateFinder.DEFAULT_MAX_DISTANCE, "distance was $distance")
    }

    @Test
    fun `distance counts differing bits`() {
        assertEquals(0, PerceptualHash.distance(0L, 0L))
        assertEquals(64, PerceptualHash.distance(0L, -1L))
        assertEquals(3, PerceptualHash.distance(0b1011L, 0b0000L))
    }

    @Test
    fun `brightness rising left to right sets every bit`() {
        val gradient = GrayImage(90, 80, IntArray(90 * 80) { (it % 90) * 2 })
        assertEquals(-1L, hash(gradient))
    }

    @Test
    fun `shrinking averages each cell`() {
        // 4x2 image shrunk to 2x1: each output cell averages a 2x2 block.
        val cells = PerceptualHash.shrink(intArrayOf(0, 10, 100, 200, 20, 30, 100, 0), 4, 2, 2, 1)
        assertEquals(15.0, cells[0], 1e-9)
        assertEquals(100.0, cells[1], 1e-9)
    }
}
