package com.sortr.app.media

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import com.sortr.data.media.MediaLocator
import com.sortr.domain.model.Photo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Builds the single system dialog that asks the user to confirm deleting several photos.
 * Android performs the deletion only if the user accepts, so the app can never delete a photo
 * without that confirmation. Files from hidden folders (WhatsApp's sent media) aren't accepted
 * by Android's dialog; those are left out here and confirmed in Sortr's own dialog instead.
 */
class DeleteRequestFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locator: MediaLocator,
) {
    /** Null if none of the photos can be put in a system request. */
    fun create(photos: Collection<Photo>): IntentSender? = create(photos.map { it.uri.toUri() })

    suspend fun createForIds(ids: Collection<Long>): IntentSender? = create(locator.urisOf(ids).values.toList())

    private fun create(uris: List<Uri>): IntentSender? {
        val requestable = uris.mapNotNull { locator.mediaStoreUri(it) }
        if (requestable.isEmpty()) return null
        return MediaStore.createDeleteRequest(context.contentResolver, requestable).intentSender
    }
}
