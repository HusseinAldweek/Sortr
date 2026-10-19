package com.sortr.domain.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlurMeterTest {

    private fun score(image: GrayImage) = BlurMeter.laplacianVariance(image.pixels, image.width, image.height)

    @Test
    fun `a flat image has no edges at all`() {
        assertEquals(0.0, score(GrayImage.flat(120)), 1e-9)
    }

    @Test
    fun `blurring lowers the score, more blur lowers it more`() {
        val sharp = GrayImage.scene(7)
        val soft = sharp.blurred(1)
        val verySoft = sharp.blurred(3)

        assertTrue(score(sharp) > score(soft))
        assertTrue(score(soft) > score(verySoft))
    }

    @Test
    fun `a sharp scene is not flagged, a heavily blurred one is`() {
        val sharp = GrayImage.scene(8)
        assertFalse(BlurMeter.isBlurry(score(sharp)))
        assertTrue(BlurMeter.isBlurry(score(sharp.blurred(4))))
    }

    @Test
    fun `a fine checkerboard is extremely sharp`() {
        val board = GrayImage(32, 32, IntArray(32 * 32) { if ((it % 32 + it / 32) % 2 == 0) 0 else 255 })
        assertTrue(score(board) > 10_000)
    }

    @Test
    fun `unscored and failed photos are never flagged`() {
        assertFalse(BlurMeter.isBlurry(null))
        assertFalse(BlurMeter.isBlurry(-1.0))
    }

    @Test
    fun `tiny images score zero instead of failing`() {
        assertEquals(0.0, BlurMeter.laplacianVariance(intArrayOf(1, 2, 3, 4), 2, 2))
    }
}
