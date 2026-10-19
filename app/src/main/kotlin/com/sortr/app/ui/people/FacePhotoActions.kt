package com.sortr.app.ui.people

import com.sortr.domain.model.Decision
import com.sortr.domain.model.FaceRef
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.model.SwipeAction
import com.sortr.domain.repository.PhotoRepository
import javax.inject.Inject

/** What the face screens can do with the photos behind selected faces: view, share, trash. */
class FacePhotoActions @Inject constructor(private val photos: PhotoRepository) {

    /** Each photo once, in the order its faces were selected. */
    suspend fun photosOf(faces: Collection<FaceRef>): List<Photo> = photos.photosByIds(faces.map { it.photoId }.distinct())

    /** Moves the photos to Sortr's Trash, like swiping left. Returns what Undo needs. */
    suspend fun trash(faces: Collection<FaceRef>): List<Decision> =
        photos.applyDecisions(
            photosOf(faces).filter { it.status != PhotoStatus.TRASHED && it.status != PhotoStatus.VAULTED },
            SwipeAction.DELETE,
        )

    suspend fun undo(decisions: Collection<Decision>) = photos.undoDecisions(decisions)
}
