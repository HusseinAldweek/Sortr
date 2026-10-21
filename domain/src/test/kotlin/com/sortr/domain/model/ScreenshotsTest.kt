package com.sortr.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenshotsTest {

    @Test
    fun `the biggest screenshot album Android can move into is home`() {
        val summary = ScreenshotSummary(
            folders = listOf(
                ScreenshotFolder("Pictures/Screenshots/", 40),
                ScreenshotFolder("DCIM/Screenshots/", 120),
                ScreenshotFolder("Download/", 3),
            ),
        )
        assertEquals("DCIM/Screenshots/", summary.homeAlbum)
        assertEquals(43, summary.outsideHome)
        assertEquals(2, summary.otherFolders)
    }

    @Test
    fun `folders Android won't move into are never home`() {
        val folders = listOf(
            ScreenshotFolder("Android/media/com.example/Screenshots/", 500),
            ScreenshotFolder("DCIM/Camera/", 2),
        )
        assertEquals(ScreenshotRules.DEFAULT_ALBUM, ScreenshotRules.homeAlbum(folders))
    }

    @Test
    fun `with no screenshot album everything goes to the default one`() {
        val summary = ScreenshotSummary(folders = listOf(ScreenshotFolder("Download/", 5)))
        assertEquals(ScreenshotRules.DEFAULT_ALBUM, summary.homeAlbum)
        assertEquals(5, summary.outsideHome)
    }
}
