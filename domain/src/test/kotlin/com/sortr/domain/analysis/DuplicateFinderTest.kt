package com.sortr.domain.analysis

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicateFinderTest {

    private fun hash(image: GrayImage) = PerceptualHash.dHash(image.pixels, image.width, image.height)

    @Test
    fun `copies of the same scene are grouped and different scenes are not`() {
        val a = GrayImage.scene(10)
        val b = GrayImage.scene(11)
        val photos = listOf(
            HashedPhoto(1, hash(a)),
            HashedPhoto(2, hash(b)),
            HashedPhoto(3, hash(a.map { it + 15 })),
            HashedPhoto(4, hash(a.resized(80, 60))),
            HashedPhoto(5, hash(GrayImage.scene(12))),
        )

        assertEquals(listOf(listOf(1L, 3L, 4L)), DuplicateFinder.groups(photos))
    }

    @Test
    fun `exact duplicates group together`() {
        val photos = listOf(HashedPhoto(1, 0x1234L), HashedPhoto(2, 0x1234L), HashedPhoto(3, 0x1234L))
        assertEquals(listOf(listOf(1L, 2L, 3L)), DuplicateFinder.groups(photos))
    }

    @Test
    fun `the distance limit is respected`() {
        val base = 0L
        val photos = listOf(
            HashedPhoto(1, base),
            HashedPhoto(2, 0b111111L), // 6 bits away: within the limit
            HashedPhoto(3, 0b1111111L shl 20), // 7 bits away from base, 13 from photo 2
        )

        assertEquals(listOf(listOf(1L, 2L)), DuplicateFinder.groups(photos, maxDistance = 6))
        assertEquals(emptyList(), DuplicateFinder.groups(photos, maxDistance = 5))
    }

    @Test
    fun `chains of near copies form one group`() {
        // 1–2 and 2–3 are close, 1–3 are 8 bits apart: still one burst.
        val photos = listOf(HashedPhoto(1, 0L), HashedPhoto(2, 0b1111L), HashedPhoto(3, 0b11111111L))
        assertEquals(listOf(listOf(1L, 2L, 3L)), DuplicateFinder.groups(photos, maxDistance = 4))
    }

    @Test
    fun `the tree finds the same groups as comparing every pair`() {
        val random = Random(99)
        val centres = List(30) { random.nextLong() }
        val photos = (0 until 300).map { i ->
            var hash = centres[i % centres.size]
            repeat(random.nextInt(0, 4)) { hash = hash xor (1L shl random.nextInt(64)) }
            HashedPhoto(i.toLong(), hash)
        }

        val fromTree = DuplicateFinder.groups(photos, maxDistance = 6).map { it.toSet() }.toSet()
        val bruteForce = bruteForceGroups(photos, 6)

        assertEquals(bruteForce, fromTree)
    }

    @Test
    fun `fewer than two photos never form a group`() {
        assertTrue(DuplicateFinder.groups(emptyList()).isEmpty())
        assertTrue(DuplicateFinder.groups(listOf(HashedPhoto(1, 5L))).isEmpty())
    }

    @Test
    fun `the best copy has the most pixels, then is sharpest, then largest, then oldest`() {
        fun c(id: Long, pixels: Long, sharp: Double?, size: Long, date: Long) = DuplicateCandidate(id, pixels, sharp, size, date)

        assertEquals(2L, DuplicateFinder.pickBest(listOf(c(1, 1_000, 500.0, 900, 1), c(2, 4_000, 10.0, 100, 2))))
        assertEquals(1L, DuplicateFinder.pickBest(listOf(c(1, 4_000, 300.0, 100, 1), c(2, 4_000, 200.0, 900, 2))))
        assertEquals(2L, DuplicateFinder.pickBest(listOf(c(1, 4_000, null, 100, 1), c(2, 4_000, null, 900, 2))))
        assertEquals(1L, DuplicateFinder.pickBest(listOf(c(1, 4_000, null, 500, 10), c(2, 4_000, null, 500, 20))))
    }

    private fun bruteForceGroups(photos: List<HashedPhoto>, maxDistance: Int): Set<Set<Long>> {
        val parent = IntArray(photos.size) { it }
        fun find(x: Int): Int = if (parent[x] == x) x else find(parent[x]).also { parent[x] = it }
        for (i in photos.indices) for (j in i + 1 until photos.size) {
            if (PerceptualHash.distance(photos[i].hash, photos[j].hash) <= maxDistance) parent[find(i)] = find(j)
        }
        return photos.indices.groupBy(::find).values.filter { it.size >= 2 }.map { g -> g.map { photos[it].id }.toSet() }.toSet()
    }
}
