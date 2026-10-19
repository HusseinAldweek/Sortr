package com.sortr.domain.faces

import kotlin.test.Test
import kotlin.test.assertEquals

class FaceAlignmentTest {

    private val delta = 1e-3f

    private fun assertNear(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, delta, "x")
        assertEquals(expected.y, actual.y, delta, "y")
    }

    @Test
    fun `level eyes land on the template positions`() {
        val t = FaceAlignment.eyesToTemplate(Point(400f, 300f), Point(500f, 300f), 112)

        assertNear(FaceAlignment.TEMPLATE_LEFT_EYE, t.map(Point(400f, 300f)))
        assertNear(FaceAlignment.TEMPLATE_RIGHT_EYE, t.map(Point(500f, 300f)))
    }

    @Test
    fun `tilted eyes are straightened`() {
        // Head tilted: right eye lower than the left.
        val left = Point(200f, 200f)
        val right = Point(280f, 260f)

        val t = FaceAlignment.eyesToTemplate(left, right, 112)

        assertNear(FaceAlignment.TEMPLATE_LEFT_EYE, t.map(left))
        assertNear(FaceAlignment.TEMPLATE_RIGHT_EYE, t.map(right))
    }

    @Test
    fun `template scales with the output size`() {
        val t = FaceAlignment.eyesToTemplate(Point(10f, 10f), Point(30f, 10f), 224)

        assertNear(Point(FaceAlignment.TEMPLATE_LEFT_EYE.x * 2, FaceAlignment.TEMPLATE_LEFT_EYE.y * 2), t.map(Point(10f, 10f)))
    }

    @Test
    fun `box fallback centres the face and keeps a margin`() {
        val t = FaceAlignment.boxToCrop(100f, 100f, 200f, 200f, 112, margin = 0.15f)

        assertNear(Point(56f, 56f), t.map(Point(150f, 150f)))
        // The box edge sits inside the crop, leaving the margin around it.
        val corner = t.map(Point(100f, 100f))
        assertEquals(112f * 0.15f / 1.3f, corner.x, 0.01f)
    }
}
