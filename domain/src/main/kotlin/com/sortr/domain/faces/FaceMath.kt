package com.sortr.domain.faces

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Vector maths for face embeddings. */
object FaceMath {

    fun dot(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding sizes differ: ${a.size} vs ${b.size}" }
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    fun norm(v: FloatArray): Float = sqrt(dot(v, v))

    /** Unit-length copy of [v]. A zero vector is returned unchanged. */
    fun l2Normalize(v: FloatArray): FloatArray {
        val length = norm(v)
        if (length == 0f) return v.copyOf()
        return FloatArray(v.size) { v[it] / length }
    }

    /** Cosine similarity in [-1, 1]; 0 if either vector is zero. Neither input needs to be normalised. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        val lengths = norm(a) * norm(b)
        if (lengths == 0f) return 0f
        return (dot(a, b) / lengths).coerceIn(-1f, 1f)
    }

    /** Little-endian float32 bytes, for storing embeddings as BLOBs. */
    fun encode(v: FloatArray): ByteArray =
        ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(v) }.array()

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "Embedding blob size ${bytes.size} isn't a multiple of 4" }
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also(floats::get)
    }
}
