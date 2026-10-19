package com.sortr.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.sortr.data.db.SortrDatabase
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.MoveResult
import com.sortr.domain.model.Photo
import com.sortr.domain.repository.GallerySync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves photos and videos into another album folder by changing their MediaStore path. The file
 * itself is untouched: same pixels, same id, same review status, same faces. Needs the write
 * access the user grants in the system dialog first.
 */
@Singleton
class MediaMover @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SortrDatabase,
    private val sync: GallerySync,
) {
    private val photoDao = db.photoDao()

    suspend fun move(photos: Collection<Photo>, relativePath: String): MoveResult = withContext(Dispatchers.IO) {
        val bucketName = AlbumPaths.displayName(relativePath)
        var moved = 0
        var failed = 0
        for (photo in photos) {
            if (photo.relativePath == relativePath) continue
            val uri = photo.uri.toUri()
            if (moveOne(uri, photo.displayName, relativePath)) {
                // Record the new location and modified time so the next sync doesn't mistake
                // the move for an edit and scan the photo again.
                photoDao.setMoved(photo.id, relativePath, bucketName, dateModified(uri) ?: 0L)
                moved++
            } else {
                failed++
            }
        }
        if (moved > 0) sync.requestSync()
        MoveResult(moved = moved, failed = failed)
    }

    /** Tries the original name first, then "name (2)", "name (3)" if the album already has one. */
    private fun moveOne(uri: Uri, displayName: String, relativePath: String): Boolean {
        val base = displayName.substringBeforeLast('.')
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
        val names = listOf(displayName) + (2..MAX_NAME_TRIES).map { n ->
            if (extension.isEmpty()) "$base ($n)" else "$base ($n).$extension"
        }
        for ((index, name) in names.withIndex()) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                if (index > 0) put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            }
            val updated = try {
                context.contentResolver.update(uri, values, null, null) > 0
            } catch (e: SecurityException) {
                Log.w(TAG, "No write access to $uri", e)
                return false
            } catch (e: Exception) {
                Log.i(TAG, "Couldn't move $uri as $name", e)
                false
            }
            if (updated) return true
        }
        return false
    }

    private fun dateModified(uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }

    private companion object {
        const val TAG = "MediaMover"
        const val MAX_NAME_TRIES = 4
    }
}
