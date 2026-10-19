package com.sortr.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.sortr.data.db.PhotoEntity
import com.sortr.data.db.SQL_CHUNK
import com.sortr.data.db.SortrDatabase
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.MoveResult
import com.sortr.domain.model.MovedCopy
import com.sortr.domain.model.Photo
import com.sortr.domain.repository.GallerySync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves photos and videos into another album folder.
 *
 * Normally only the MediaStore path changes: same file, same id, same review status, same
 * faces. That needs the write access the user grants in the system dialog first. Android
 * refuses that for some files, notably those in another app's own folder (WhatsApp's
 * `Android/media/com.whatsapp/...`) and files read from hidden folders. Those are copied into
 * the album instead, keeping their date and review status; the caller then asks the user to
 * remove the originals and calls [completeCopies], which undoes copies whose original stayed.
 */
@Singleton
class MediaMover @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SortrDatabase,
    private val sync: GallerySync,
    private val locator: MediaLocator,
) {
    private val photoDao = db.photoDao()

    suspend fun move(photos: Collection<Photo>, relativePath: String): MoveResult = withContext(Dispatchers.IO) {
        val bucketName = AlbumPaths.displayName(relativePath)
        var moved = 0
        var failed = 0
        val copies = ArrayList<MovedCopy>()
        for (photo in photos) {
            if (photo.relativePath == relativePath) continue
            val uri = locator.mediaStoreUri(photo.uri.toUri())
            if (uri != null && moveInPlace(uri, photo.displayName, relativePath)) {
                // Record the new location and modified time so the next sync doesn't mistake
                // the move for an edit and scan the photo again.
                photoDao.setMoved(photo.id, relativePath, bucketName, dateModified(uri) ?: 0L)
                moved++
                continue
            }
            val copy = copyInto(photo, relativePath, bucketName)
            if (copy != null) copies += copy else failed++
        }
        if (moved > 0 || copies.isNotEmpty()) sync.requestSync()
        MoveResult(moved = moved, failed = failed, copies = copies)
    }

    /**
     * Keeps the copies whose original is gone (the user allowed removing it) and deletes the
     * others, so nothing is left twice. Returns how many copies were kept.
     */
    suspend fun completeCopies(copies: Collection<MovedCopy>): Int = withContext(Dispatchers.IO) {
        if (copies.isEmpty()) return@withContext 0
        val stillThere = locator.existingIds(copies.map { it.original.id })
        val (kept, undone) = copies.partition { it.original.id !in stillThere }
        // The originals' rows go (their faces with them); the copies carry the review status.
        kept.map { it.original.id }.chunked(SQL_CHUNK).forEach { photoDao.deleteByIds(it) }
        for (copy in undone) {
            // Sortr created this file itself, so it may delete it without asking.
            runCatching { context.contentResolver.delete(copy.copyUri.toUri(), null, null) }
                .onFailure { Log.w(TAG, "Couldn't remove copy ${copy.copyUri}", it) }
        }
        undone.map { it.copyId }.chunked(SQL_CHUNK).forEach { photoDao.deleteByIds(it) }
        sync.requestSync()
        kept.size
    }

    /** Tries the original name first, then "name (2)", "name (3)" if the album already has one. */
    private fun moveInPlace(uri: Uri, displayName: String, relativePath: String): Boolean {
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
            try {
                if (context.contentResolver.update(uri, values, null, null) > 0) return true
            } catch (e: SecurityException) {
                Log.w(TAG, "No write access to $uri", e)
                return false
            } catch (e: IllegalArgumentException) {
                // e.g. "Changing ownership ... is not allowed": another name won't help.
                Log.i(TAG, "Android won't move $uri in place: ${e.message}")
                return false
            } catch (e: Exception) {
                Log.i(TAG, "Couldn't move $uri as $name", e)
            }
        }
        return false
    }

    /**
     * Copies the file into the album as a new gallery item owned by Sortr, with the original's
     * name (Android adds " (1)" if taken), type and capture date, and records it with the
     * original's review status. Null if the copy failed; nothing is left behind then.
     */
    private suspend fun copyInto(photo: Photo, relativePath: String, bucketName: String): MovedCopy? {
        val resolver = context.contentResolver
        val collection = if (photo.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, photo.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, photo.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.DATE_TAKEN, photo.dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val copyUri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't create a copy of ${photo.uri} in $relativePath", e)
            null
        } ?: return null
        return try {
            resolver.openInputStream(photo.uri.toUri())?.use { input ->
                resolver.openOutputStream(copyUri)?.use { output -> input.copyTo(output) }
                    ?: throw IllegalStateException("No output for $copyUri")
            } ?: throw IllegalStateException("Can't read ${photo.uri}")
            resolver.update(copyUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            val copyId = ContentUris.parseId(copyUri)
            // A zero modified time makes the next sync fill in the real metadata.
            photoDao.insertAll(
                listOf(
                    PhotoEntity(
                        mediaStoreId = copyId,
                        uri = copyUri.toString(),
                        displayName = photo.displayName,
                        mimeType = photo.mimeType,
                        dateTaken = photo.dateTaken,
                        size = photo.sizeBytes,
                        width = photo.width,
                        height = photo.height,
                        bucketName = bucketName,
                        relativePath = relativePath,
                        dateModified = 0,
                        status = photo.status,
                        statusChangedAt = System.currentTimeMillis(),
                        mediaType = photo.mediaType,
                        durationMs = photo.durationMs,
                    ),
                ),
            )
            MovedCopy(original = photo, copyId = copyId, copyUri = copyUri.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't copy ${photo.uri} to $relativePath", e)
            runCatching { resolver.delete(copyUri, null, null) }
            null
        }
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
