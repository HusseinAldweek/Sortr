package com.sortr.data.media

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.room.withTransaction
import com.sortr.data.db.PhotoEntity
import com.sortr.data.db.PhotoMetadataUpdate
import com.sortr.data.db.SQL_CHUNK
import com.sortr.data.db.SortrDatabase
import com.sortr.data.di.ApplicationScope
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.repository.GallerySync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors MediaStore into the photos table. Each run reads the whole image list (cheap, even
 * for tens of thousands of rows) and writes only what changed: new photos, edited photos, and
 * rows whose photo no longer exists. Review status is never touched for photos that still exist.
 */
@Singleton
class GallerySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SortrDatabase,
    private val media: MediaStoreSource,
    @ApplicationScope scope: CoroutineScope,
) : GallerySync {

    private val photoDao = db.photoDao()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val _isSyncing = MutableStateFlow(false)

    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            for (request in requests) {
                _isSyncing.value = true
                try {
                    syncNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Gallery sync failed", e)
                } finally {
                    _isSyncing.value = false
                }
            }
        }
    }

    override fun requestSync() {
        requests.trySend(Unit)
    }

    @OptIn(FlowPreview::class)
    override fun mediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.debounce(CHANGE_DEBOUNCE_MS)

    override suspend fun existingIds(ids: Collection<Long>): Set<Long> =
        withContext(Dispatchers.IO) { media.existingIds(ids) }

    private suspend fun syncNow() {
        // Without read access MediaStore returns an empty list, which would look like every
        // photo had been deleted and wipe all review progress.
        if (!media.hasReadAccess()) return
        val images = media.queryAllImages() ?: return

        val known = photoDao.syncKeys().associateBy { it.mediaStoreId }
        val now = System.currentTimeMillis()
        val inserts = ArrayList<PhotoEntity>()
        val updates = ArrayList<PhotoMetadataUpdate>()
        val seen = HashSet<Long>(images.size)

        for (image in images) {
            seen += image.id
            val existing = known[image.id]
            when {
                existing == null -> inserts += image.toNewEntity(now)
                existing.dateModified != image.dateModified || existing.size != image.size ->
                    updates += image.toMetadataUpdate()
            }
        }
        val removed = known.keys.filterNot(seen::contains)

        inserts.chunked(SQL_CHUNK).forEach { db.withTransaction { photoDao.insertAll(it) } }
        updates.chunked(SQL_CHUNK).forEach { chunk ->
            db.withTransaction {
                photoDao.updateMetadata(chunk)
                // An edited photo gets its faces found again.
                db.faceDao().deleteForPhotos(chunk.map { it.mediaStoreId })
            }
        }
        removed.chunked(SQL_CHUNK).forEach { photoDao.deleteByIds(it) }

        Log.i(TAG, "Sync: ${images.size} images, +${inserts.size} ~${updates.size} -${removed.size}")
    }

    private fun MediaImage.toNewEntity(now: Long) = PhotoEntity(
        mediaStoreId = id,
        uri = media.uriFor(id).toString(),
        displayName = displayName,
        mimeType = mimeType,
        dateTaken = dateTaken,
        size = size,
        width = width,
        height = height,
        bucketName = bucketName,
        relativePath = relativePath,
        dateModified = dateModified,
        status = PhotoStatus.UNREVIEWED,
        statusChangedAt = now,
    )

    private fun MediaImage.toMetadataUpdate() = PhotoMetadataUpdate(
        mediaStoreId = id,
        uri = media.uriFor(id).toString(),
        displayName = displayName,
        mimeType = mimeType,
        dateTaken = dateTaken,
        size = size,
        width = width,
        height = height,
        bucketName = bucketName,
        relativePath = relativePath,
        dateModified = dateModified,
    )

    private companion object {
        const val TAG = "GallerySync"
        const val CHANGE_DEBOUNCE_MS = 1_500L
    }
}
