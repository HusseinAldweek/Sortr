package com.sortr.domain.model

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryTest {

    @Test
    fun `sources are recognised from folder paths, ignoring case`() {
        assertTrue(MediaSource.SCREENSHOTS.matches("Pictures/Screenshots/"))
        assertTrue(MediaSource.SCREENSHOTS.matches("DCIM/screenshots/"))
        assertTrue(MediaSource.SNAPCHAT.matches("Pictures/Snapchat/"))
        assertTrue(MediaSource.WHATSAPP.matches("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"))
        assertTrue(MediaSource.CAMERA.matches("DCIM/Camera/"))
        assertTrue(MediaSource.DOWNLOADS.matches("Download/"))
        assertFalse(MediaSource.CAMERA.matches("Pictures/Camera roll copy/"))
        assertFalse(MediaSource.DOWNLOADS.matches("Pictures/Downloaded stuff/"))
        assertFalse(MediaSource.SNAPCHAT.matches(null))
        val sent = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent/"
        assertTrue(MediaSource.WHATSAPP_SENT.matches(sent))
        assertTrue(MediaSource.WHATSAPP_PHOTOS.matches(sent))
        assertFalse(MediaSource.WHATSAPP_VIDEOS.matches(sent))
        assertTrue(MediaSource.WHATSAPP_VIDEOS.matches("Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Video/"))
        assertTrue(MediaSource.WHATSAPP_PRIVATE.matches("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Private/"))
        assertFalse(MediaSource.WHATSAPP_PRIVATE.matches("Pictures/Private/"))
    }

    @Test
    fun `regex characters in patterns are taken literally`() {
        // A dot in a folder name must not act as a wildcard.
        assertFalse(MediaSource.CAMERA.matches("DCIMxCamera/"))
    }

    @Test
    fun `only DCIM and Pictures folders can receive moved photos`() {
        assertTrue(AlbumPaths.isPhotoFolder("DCIM/Camera/"))
        assertTrue(AlbumPaths.isPhotoFolder("Pictures/Holidays/"))
        assertFalse(AlbumPaths.isPhotoFolder("Download/"))
        assertFalse(AlbumPaths.isPhotoFolder("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"))
    }

    @Test
    fun `renaming keeps the parent folder when photos are allowed there`() {
        assertEquals("DCIM/Trip/", AlbumPaths.renamed("DCIM/Camera/", "Trip"))
        assertEquals("Pictures/Old/Trip/", AlbumPaths.renamed("Pictures/Old/Holiday/", "Trip"))
        // A WhatsApp folder can't take photos back, so the renamed album goes to Pictures.
        assertEquals("Pictures/Trip/", AlbumPaths.renamed("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", "Trip"))
        assertEquals("Pictures/Trip/", AlbumPaths.renamed("Download/", "Trip"))
    }

    @Test
    fun `album names are cleaned for use as folder names`() {
        assertEquals("Summer 2024", AlbumPaths.cleanName("  Summer 2024  "))
        assertEquals("ab", AlbumPaths.cleanName("a/b"))
        assertEquals("trip", AlbumPaths.cleanName("..trip.."))
        assertNull(AlbumPaths.cleanName("  /  "))
    }

    @Test
    fun `display name is the last folder`() {
        assertEquals("Camera", AlbumPaths.displayName("DCIM/Camera/"))
        assertEquals("WhatsApp Images", AlbumPaths.displayName("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"))
        assertEquals(
            "WhatsApp Images · Sent",
            AlbumPaths.displayName("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent/"),
        )
        assertEquals(
            "WhatsApp Business Video · Private",
            AlbumPaths.displayName("Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Video/Private/"),
        )
    }

    @Test
    fun `date ranges follow the given time zone`() {
        val zone = ZoneId.of("Asia/Baghdad")
        val year = DateRanges.year(2024, zone)
        assertEquals(Instant.parse("2023-12-31T21:00:00Z").toEpochMilli(), year.first)
        assertEquals(Instant.parse("2024-12-31T21:00:00Z").toEpochMilli() - 1, year.last)

        val feb = DateRanges.month(2024, 2, zone)
        assertEquals(Instant.parse("2024-01-31T21:00:00Z").toEpochMilli(), feb.first)
        // 2024 is a leap year: February has 29 days.
        assertEquals(Instant.parse("2024-02-29T21:00:00Z").toEpochMilli() - 1, feb.last)
    }

    @Test
    fun `trashed and vaulted photos count as sorted`() {
        // 100 photos in all: 40 trashed, 10 vaulted, 20 kept, 30 left.
        val summary = LibrarySummary(total = 50, unreviewed = 25, skipped = 5, kept = 20, trashedCount = 40, vaultedCount = 10)
        assertEquals(30, summary.toReview)
        assertEquals(0.7f, summary.sortedFraction, 0.0001f)
        assertEquals(0f, LibrarySummary().sortedFraction)
    }
}
