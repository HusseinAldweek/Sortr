package com.sortr.domain.faces

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FaceMathTest {

    private val delta = 1e-5f

    @Test
    fun `identical directions have similarity one regardless of length`() {
        assertEquals(1f, FaceMath.cosine(floatArrayOf(1f, 2f, 3f), floatArrayOf(2f, 4f, 6f)), delta)
    }

    @Test
    fun `opposite directions have similarity minus one`() {
        assertEquals(-1f, FaceMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(-3f, 0f)), delta)
    }

    @Test
    fun `perpendicular vectors have similarity zero`() {
        assertEquals(0f, FaceMath.cosine(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 5f, 0f)), delta)
    }

    @Test
    fun `cosine of a known angle`() {
        // 45 degrees.
        assertEquals((1 / sqrt(2.0)).toFloat(), FaceMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(1f, 1f)), delta)
    }

    @Test
    fun `a zero vector has similarity zero instead of NaN`() {
        assertEquals(0f, FaceMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)))
    }

    @Test
    fun `normalising gives unit length and keeps direction`() {
        val v = floatArrayOf(3f, 4f)
        val unit = FaceMath.l2Normalize(v)

        assertEquals(1f, FaceMath.norm(unit), delta)
        assertEquals(0.6f, unit[0], delta)
        assertEquals(0.8f, unit[1], delta)
        // The input is left alone.
        assertContentEquals(floatArrayOf(3f, 4f), v)
    }

    @Test
    fun `normalising a zero vector leaves it zero`() {
        assertContentEquals(floatArrayOf(0f, 0f), FaceMath.l2Normalize(floatArrayOf(0f, 0f)))
    }

    @Test
    fun `embeddings survive the round trip through bytes`() {
        val v = FloatArray(192) { it * 0.37f - 20f }
        assertContentEquals(v, FaceMath.decode(FaceMath.encode(v)))
        assertEquals(192 * 4, FaceMath.encode(v).size)
    }

    @Test
    fun `mismatched sizes are rejected`() {
        assertFailsWith<IllegalArgumentException> { FaceMath.dot(floatArrayOf(1f), floatArrayOf(1f, 2f)) }
        assertFailsWith<IllegalArgumentException> { FaceMath.decode(ByteArray(5)) }
    }
}
