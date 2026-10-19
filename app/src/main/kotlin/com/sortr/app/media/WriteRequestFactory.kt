package com.sortr.app.media

import android.content.Context
import android.content.IntentSender
import android.provider.MediaStore
import androidx.core.net.toUri
import com.sortr.domain.model.Photo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Builds the system dialog that asks the user to let Sortr change several photos at once,
 * here to move them into another album. Nothing is changed unless the user allows it.
 */
class WriteRequestFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun create(photos: Collection<Photo>): IntentSender =
        MediaStore.createWriteRequest(context.contentResolver, photos.map { it.uri.toUri() }).intentSender

    companion object {
        /** Items per dialog; keeps the request well inside Android's size limit for one call. */
        const val MAX_PER_REQUEST = 1_000
    }
}
