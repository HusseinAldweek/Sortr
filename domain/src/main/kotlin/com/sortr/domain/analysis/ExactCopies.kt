package com.sortr.domain.analysis

import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus

/** Rules for groups of byte-identical files. */
object ExactCopies {

    /**
     * The copy to keep, since the files themselves are the same: one the user already kept, then
     * one the gallery can see, then one in the camera folder, then the oldest.
     */
    fun pickKeeper(copies: List<Photo>): Long =
        copies.sortedWith(
            compareByDescending<Photo> { it.status == PhotoStatus.KEPT }
                .thenBy { it.fromHiddenFolder }
                .thenByDescending { it.relativePath?.startsWith(CAMERA_FOLDER, ignoreCase = true) == true }
                .thenBy { it.dateTaken }
                .thenBy { it.id },
        ).first().id

    private const val CAMERA_FOLDER = "DCIM/Camera/"
}
