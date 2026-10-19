package com.sortr.domain.faces

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FaceRulesTest {

    @Test
    fun `faces smaller than 80 pixels are skipped`() {
        assertNull(FaceRules.classify(79, 120, 0f, 0f, 0f))
        assertNull(FaceRules.classify(120, 79, 0f, 0f, 0f))
    }

    @Test
    fun `heads turned too far are skipped`() {
        assertNull(FaceRules.classify(200, 200, yaw = 45f, pitch = 0f, roll = 0f))
        assertNull(FaceRules.classify(200, 200, yaw = 0f, pitch = -35f, roll = 0f))
        assertNull(FaceRules.classify(200, 200, yaw = 0f, pitch = 0f, roll = 60f))
    }

    @Test
    fun `large frontal faces are good`() {
        assertEquals(FaceQuality.GOOD, FaceRules.classify(150, 160, yaw = 10f, pitch = -5f, roll = 30f))
    }

    @Test
    fun `a face reported twice with nested boxes is kept once, as the larger box`() {
        // Taken from a real duplicate: the second box sits almost entirely inside the first.
        val boxes = listOf(PixelBox(502, 332, 1035, 1034), PixelBox(617, 400, 1030, 818), PixelBox(521, 479, 956, 878))

        assertEquals(listOf(0), FaceRules.distinctFaces(boxes))
    }

    @Test
    fun `separate faces in a group photo are all kept`() {
        val boxes = listOf(PixelBox(100, 100, 300, 300), PixelBox(400, 120, 580, 300), PixelBox(700, 90, 900, 310))

        assertEquals(listOf(0, 1, 2), FaceRules.distinctFaces(boxes))
    }

    @Test
    fun `faces that only touch slightly are both kept`() {
        // Overlap is 20% of the smaller box: two people standing close.
        val boxes = listOf(PixelBox(0, 0, 100, 100), PixelBox(80, 0, 180, 100))

        assertEquals(listOf(0, 1), FaceRules.distinctFaces(boxes))
    }

    @Test
    fun `empty boxes are dropped`() {
        assertEquals(listOf(1), FaceRules.distinctFaces(listOf(PixelBox(5, 5, 5, 50), PixelBox(0, 0, 100, 100))))
    }

    @Test
    fun `small or turned faces are weak`() {
        assertEquals(FaceQuality.WEAK, FaceRules.classify(90, 90, 0f, 0f, 0f))
        assertEquals(FaceQuality.WEAK, FaceRules.classify(200, 200, yaw = 30f, pitch = 0f, roll = 0f))
    }
}
