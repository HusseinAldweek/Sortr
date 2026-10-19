package com.sortr.app.media

import android.content.Context
import android.content.IntentSender
import android.provider.MediaStore
import androidx.core.net.toUri
import com.sortr.domain.model.Photo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Builds the single system dialog that asks the user to confirm deleting several photos.
 * Android performs the deletion only if the user accepts, so the app can never delete a photo
 * without that confirmation.
 */
class DeleteRequestFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun create(photos: Collection<Photo>): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, photos.map { it.uri.toUri() }).intentSender
}
