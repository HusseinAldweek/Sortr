package com.sortr.app.media

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
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
    fun create(photos: Collection<Photo>): IntentSender = create(photos.map { it.uri.toUri() })

    fun createForIds(mediaStoreIds: Collection<Long>): IntentSender {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        return create(mediaStoreIds.map { ContentUris.withAppendedId(collection, it) })
    }

    private fun create(uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
}
