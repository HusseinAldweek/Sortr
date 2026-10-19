package com.sortr.domain.faces

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FaceClustererTest {

    private val clusterer = FaceClusterer(threshold = 0.6f, unsureMargin = 0.08f)

    /** A unit vector at [degrees] from the x axis, in the first two dimensions of a 4D space. */
    private fun at(degrees: Double): FloatArray {
        val r = Math.toRadians(degrees)
        return floatArrayOf(cos(r).toFloat(), sin(r).toFloat(), 0f, 0f)
    }

    private fun good(v: FloatArray, rejected: Long? = null) = FaceSample(v, FaceQuality.GOOD, rejected)
    private fun weak(v: FloatArray) = FaceSample(v, FaceQuality.WEAK)

    @Test
    fun `a face close to a person joins them`() {
        val people = listOf(Centroid(1, at(0.0), 3))
        // cos 30° ≈ 0.87
        val placement = clusterer.place(good(at(30.0)), people)

        assertEquals(Placement.Existing(1, placement.similarity), placement)
        assertEquals(0.866f, placement.similarity, 0.01f)
    }

    @Test
    fun `a face far from everyone starts a new person`() {
        // cos 70° ≈ 0.34, well below the threshold.
        assertIs<Placement.NewPerson>(clusterer.place(good(at(70.0)), listOf(Centroid(1, at(0.0), 3))))
    }

    @Test
    fun `the first face ever starts a new person`() {
        assertIs<Placement.NewPerson>(clusterer.place(good(at(0.0)), emptyList()))
    }

    @Test
    fun `a face just under the threshold is left unsorted instead of guessed`() {
        // cos 56° ≈ 0.56: below 0.6 but within the 0.08 margin.
        assertIs<Placement.Unsorted>(clusterer.place(good(at(56.0)), listOf(Centroid(1, at(0.0), 3))))
    }

    @Test
    fun `weak faces never start a new person`() {
        assertIs<Placement.Unsorted>(clusterer.place(weak(at(80.0)), listOf(Centroid(1, at(0.0), 3))))
        assertIs<Placement.Unsorted>(clusterer.place(weak(at(0.0)), emptyList()))
    }

    @Test
    fun `weak faces can still join a clear match`() {
        assertIs<Placement.Existing>(clusterer.place(weak(at(10.0)), listOf(Centroid(1, at(0.0), 3))))
    }

    @Test
    fun `the closest person wins`() {
        val people = listOf(Centroid(1, at(0.0), 3), Centroid(2, at(40.0), 3))
        // 30° from person 1, 10° from person 2.
        assertEquals(2L, (clusterer.place(good(at(30.0)), people) as Placement.Existing).personId)
    }

    @Test
    fun `a face is never matched to a person it was removed from`() {
        val people = listOf(Centroid(1, at(0.0), 3), Centroid(2, at(80.0), 3))

        val placement = clusterer.place(good(at(5.0), rejected = 1), people)

        // Person 2 is too far away, so it starts someone new rather than going back to person 1.
        assertIs<Placement.NewPerson>(placement)
    }

    @Test
    fun `the average updates after each face joins`() {
        val centroid = Centroid(1, floatArrayOf(1f, 0f), 1)

        centroid.add(floatArrayOf(0f, 1f))
        assertEquals(2, centroid.count)
        assertEquals(0.5f, centroid.mean[0], 1e-6f)
        assertEquals(0.5f, centroid.mean[1], 1e-6f)

        centroid.add(floatArrayOf(1f, 1f))
        assertEquals(3, centroid.count)
        assertEquals(2f / 3, centroid.mean[0], 1e-6f)
        assertEquals(2f / 3, centroid.mean[1], 1e-6f)
    }

    @Test
    fun `removing a face undoes adding it`() {
        val centroid = Centroid.of(1, listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f), floatArrayOf(1f, 1f)))

        assertTrue(centroid.remove(floatArrayOf(1f, 1f)))
        assertEquals(2, centroid.count)
        assertEquals(0.5f, centroid.mean[0], 1e-5f)
        assertEquals(0.5f, centroid.mean[1], 1e-5f)
    }

    @Test
    fun `removing the last face empties the person`() {
        val centroid = Centroid(1, floatArrayOf(1f, 0f), 1)
        kotlin.test.assertFalse(centroid.remove(floatArrayOf(1f, 0f)))
        assertEquals(0, centroid.count)
    }

    @Test
    fun `merging people gives the average of all their faces`() {
        val a = Centroid.of(1, listOf(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f), floatArrayOf(1f, 0f)))
        val b = Centroid.of(2, listOf(floatArrayOf(0f, 1f)))

        a.absorb(b)

        assertEquals(4, a.count)
        assertEquals(0.75f, a.mean[0], 1e-6f)
        assertEquals(0.25f, a.mean[1], 1e-6f)
    }

    @Test
    fun `placeAll separates two distinct people`() {
        val random = Random(42)
        fun jitter(base: Double) = FaceMath.l2Normalize(at(base + random.nextDouble(-10.0, 10.0)))
        val faces = List(6) { good(jitter(0.0)) } + List(6) { good(jitter(90.0)) }
        val centroids = mutableListOf<Centroid>()

        val placements = clusterer.placeAll(faces, centroids)

        assertEquals(2, centroids.size)
        val firstPerson = placements.take(6).map { (it as? Placement.Existing)?.personId ?: (it as Placement.NewPerson).personId }.toSet()
        val secondPerson = placements.drop(6).map { (it as? Placement.Existing)?.personId ?: (it as Placement.NewPerson).personId }.toSet()
        assertEquals(1, firstPerson.size)
        assertEquals(1, secondPerson.size)
        assertTrue(firstPerson != secondPerson)
        assertEquals(6, centroids.first { it.personId in firstPerson }.count)
    }

    @Test
    fun `placeAll seeds people from good faces before weak ones`() {
        // The weak face comes first in the list but must not be lost: it can join once the good face exists.
        val faces = listOf(weak(at(5.0)), good(at(0.0)))
        val centroids = mutableListOf<Centroid>()

        val placements = clusterer.placeAll(faces, centroids)

        assertIs<Placement.NewPerson>(placements[1])
        assertEquals(Placement.Existing((placements[1] as Placement.NewPerson).personId, placements[0].similarity), placements[0])
    }

    @Test
    fun `placeAll gives new people distinct negative temporary ids`() {
        val faces = listOf(good(at(0.0)), good(at(90.0)), good(at(180.0)))
        val centroids = mutableListOf<Centroid>()

        val ids = clusterer.placeAll(faces, centroids).map { (it as Placement.NewPerson).personId }

        assertEquals(listOf(-1L, -2L, -3L), ids)
    }

    @Test
    fun `a stricter threshold splits what a looser one joins`() {
        // 40° apart: cos ≈ 0.77
        val faces = listOf(good(at(0.0)), good(at(40.0)))

        val loose = mutableListOf<Centroid>().also { FaceClusterer(0.6f).placeAll(faces, it) }
        val strict = mutableListOf<Centroid>().also { FaceClusterer(0.85f, unsureMargin = 0f).placeAll(faces, it) }

        assertEquals(1, loose.size)
        assertEquals(2, strict.size)
    }
}
