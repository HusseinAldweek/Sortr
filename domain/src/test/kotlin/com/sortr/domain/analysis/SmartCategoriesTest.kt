package com.sortr.domain.analysis

import com.sortr.domain.model.ScreenshotRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartCategoriesTest {

    private fun classify(
        labels: Map<String, Float> = emptyMap(),
        text: String = "",
        path: String = "DCIM/Camera/",
        name: String = "IMG_1.jpg",
    ) = SmartCategories.of(SmartCategories.classify(ImageSignals(labels, text, path, name)))

    @Test
    fun `confident food and pet labels become categories`() {
        assertEquals(setOf(SmartCategory.FOOD), classify(labels = mapOf("Pizza" to 0.91f, "Table" to 0.8f)))
        assertEquals(setOf(SmartCategory.PETS), classify(labels = mapOf("Dog" to 0.88f)))
        assertEquals(emptySet(), classify(labels = mapOf("Dog" to 0.4f)))
    }

    @Test
    fun `receipts need receipt words and several prices`() {
        val receipt = "CAFE ROMA\nLatte 4.50\nCroissant 3.20\nWater 1.80\nTOTAL 9.50\nVISA"
        assertEquals(setOf(SmartCategory.RECEIPTS), classify(text = receipt))
        assertEquals(emptySet(), classify(text = "Total recall\n1.50"))
    }

    @Test
    fun `lots of text is a document`() {
        val page = (1..12).joinToString("\n") { "Line number $it of the contract between both parties" }
        assertEquals(setOf(SmartCategory.DOCUMENTS), classify(text = page))
    }

    @Test
    fun `short text in a picture from a chat app is a meme`() {
        val path = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"
        assertEquals(setOf(SmartCategory.MEMES), classify(text = "WHEN THE WIFI\nCOMES BACK", path = path))
        // The same picture from the camera is just a photo with a sign in it.
        assertEquals(emptySet(), classify(text = "WHEN THE WIFI\nCOMES BACK"))
    }

    @Test
    fun `screenshots are never documents or memes`() {
        val page = (1..12).joinToString("\n") { "Settings row $it with some longer text in it" }
        assertEquals(emptySet(), classify(text = page, path = "Pictures/Screenshots/", name = "Screenshot_1.png"))
    }

    @Test
    fun `screenshot rule matches folders and file names like the SQL`() {
        assertTrue(ScreenshotRules.isScreenshot("DCIM/Screenshots/", "a.png"))
        assertTrue(ScreenshotRules.isScreenshot("Download/", "Screen Shot 2024-01-01 at 10.00.png"))
        assertTrue(ScreenshotRules.isScreenshot("Download/", "screenshot_2.jpg"))
        assertFalse(ScreenshotRules.isScreenshot("DCIM/Camera/", "IMG_1.jpg"))
    }
}
