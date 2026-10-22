package com.sortr.domain.analysis

import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ExactCopiesTest {

    private fun photo(
        id: Long,
        path: String,
        dateTaken: Long = 1_000,
        status: PhotoStatus = PhotoStatus.UNREVIEWED,
        hidden: Boolean = false,
    ) = Photo(
        id = id,
        uri = "content://media/$id",
        displayName = "VID-$id.mp4",
        mimeType = "video/mp4",
        dateTaken = dateTaken,
        sizeBytes = 5_000_000,
        width = 0,
        height = 0,
        bucketName = null,
        relativePath = path,
        status = status,
        fromHiddenFolder = hidden,
    )

    @Test
    fun `a copy the user already kept wins`() {
        val copies = listOf(photo(1, "DCIM/Camera/"), photo(2, "Download/", status = PhotoStatus.KEPT))
        assertEquals(2, ExactCopies.pickKeeper(copies))
    }

    @Test
    fun `gallery copies beat hidden WhatsApp ones`() {
        val copies = listOf(photo(-5, "WhatsApp/Media/WhatsApp Video/Sent/", dateTaken = 1, hidden = true), photo(3, "Pictures/Trip/"))
        assertEquals(3, ExactCopies.pickKeeper(copies))
    }

    @Test
    fun `the camera folder beats other albums, then the oldest wins`() {
        assertEquals(4, ExactCopies.pickKeeper(listOf(photo(3, "Pictures/Trip/", dateTaken = 1), photo(4, "DCIM/Camera/", dateTaken = 9))))
        assertEquals(6, ExactCopies.pickKeeper(listOf(photo(5, "Download/", dateTaken = 9), photo(6, "Pictures/A/", dateTaken = 2))))
    }
}
