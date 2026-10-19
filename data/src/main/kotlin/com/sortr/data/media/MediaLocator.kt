package com.sortr.data.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.sortr.data.db.SQL_CHUNK
import com.sortr.data.db.SortrDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Works for both kinds of items Sortr knows: MediaStore photos (positive ids) and photos read
 * from hidden folders such as WhatsApp's sent media (negative ids, document URIs).
 */
@Singleton
class MediaLocator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SortrDatabase,
    private val media: MediaStoreSource,
) {
    private val photoDao = db.photoDao()

    /** Which of [ids] still exist on the phone. */
    suspend fun existingIds(ids: Collection<Long>): Set<Long> = withContext(Dispatchers.IO) {
        val (folderIds, storeIds) = ids.partition { it < 0 }
        val found = HashSet(media.existingIds(storeIds))
        urisOf(folderIds).forEach { (id, uri) -> if (documentExists(uri)) found += id }
        found
    }

    /** The stored URI of each id, for items that are still in the database. */
    suspend fun urisOf(ids: Collection<Long>): Map<Long, Uri> =
        ids.chunked(SQL_CHUNK).flatMap { photoDao.urisOf(it) }.associate { it.mediaStoreId to it.uri.toUri() }

    /**
     * The URI to put in Android's delete and change-permission dialogs, or null for a file read
     * from a hidden folder: Android only accepts items its gallery can see.
     */
    fun mediaStoreUri(uri: Uri): Uri? = uri.takeIf { it.authority == MediaStore.AUTHORITY }

    /**
     * Deletes hidden-folder files (negative [ids]). Only called after the user confirmed in
     * Sortr's own dialog, since Android has none for these files. Returns the ids deleted.
     */
    suspend fun deleteHidden(ids: Collection<Long>): Set<Long> = withContext(Dispatchers.IO) {
        urisOf(ids.filter { it < 0 }).filter { (_, uri) ->
            try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (e: Exception) {
                // Usually a folder permission from before Sortr asked for delete access.
                Log.w(TAG, "Couldn't delete $uri", e)
                false
            }
        }.keys
    }

    private fun documentExists(uri: Uri): Boolean = try {
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    } catch (e: Exception) {
        // Missing file, or the folder permission was taken away.
        false
    }

    private companion object {
        const val TAG = "MediaLocator"
    }
}
