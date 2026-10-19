package com.sortr.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionFilterTest {

    @Test
    fun `every filter survives the round trip through its key`() {
        listOf(SessionFilter.AllPhotos, SessionFilter.UnsortedFaces, SessionFilter.Person(42)).forEach {
            assertEquals(it, SessionFilter.fromKey(it.key))
        }
    }

    @Test
    fun `unknown or broken keys fall back to all photos`() {
        assertEquals(SessionFilter.AllPhotos, SessionFilter.fromKey("nonsense"))
        assertEquals(SessionFilter.AllPhotos, SessionFilter.fromKey("person:abc"))
    }
}
