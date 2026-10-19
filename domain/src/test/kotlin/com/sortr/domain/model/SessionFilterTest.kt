package com.sortr.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionFilterTest {

    @Test
    fun `every filter survives the round trip through its key`() {
        listOf(
            SessionFilter.AllPhotos,
            SessionFilter.UnsortedFaces,
            SessionFilter.Person(42),
            SessionFilter.Year(2019),
            SessionFilter.Month(2023, 11),
            SessionFilter.Album("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"),
            SessionFilter.Album("Pictures/Trip & Friends, 2024/"),
            SessionFilter.Source(MediaSource.SNAPCHAT),
            SessionFilter.Videos,
            SessionFilter.Blurry,
            SessionFilter.Duplicates,
            SessionFilter.Largest,
            SessionFilter.Custom(),
            SessionFilter.Custom(
                mediaType = MediaTypeFilter.VIDEOS,
                year = 2022,
                month = 7,
                albums = setOf("DCIM/Camera/", "Pictures/A,B&C=D/"),
                minSizeBytes = 5_000_000,
            ),
        ).forEach { assertEquals(it, SessionFilter.fromKey(it.key), it.key) }
    }

    @Test
    fun `unknown or broken keys fall back to all photos`() {
        listOf("nonsense", "person:abc", "month:2023-13", "source:NOPE", "year:").forEach {
            assertEquals(SessionFilter.AllPhotos, SessionFilter.fromKey(it), it)
        }
    }
}
