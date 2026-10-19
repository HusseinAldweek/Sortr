package com.sortr.data.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.sortr.data.db.SQL_CHUNK
import com.sortr.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One row of the MediaStore images or videos table. */
data class MediaItem(
    val id: Long,
    val mediaType: MediaType,
    val displayName: String,
    val mimeType: String,
    val dateTaken: Long,
    val dateModified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val bucketName: String?,
    val relativePath: String?,
    val durationMs: Long?,
)

/**
 * Reads photos and videos from MediaStore. Both live in the same MediaStore table, so their
 * ids never collide.
 */
@Singleton
class MediaStoreSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val images: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val videos: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    fun collection(type: MediaType): Uri = if (type == MediaType.VIDEO) videos else images

    fun uriFor(id: Long, type: MediaType = MediaType.IMAGE): Uri = ContentUris.withAppendedId(collection(type), id)

    /** True when the app may read at least some of the user's photos or videos. */
    fun hasReadAccess(): Boolean = hasAccess(MediaType.IMAGE) || hasAccess(MediaType.VIDEO)

    fun hasAccess(type: MediaType): Boolean {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        val images = granted(Manifest.permission.READ_MEDIA_IMAGES)
        val videos = granted(Manifest.permission.READ_MEDIA_VIDEO)
        if (if (type == MediaType.VIDEO) videos else images) return true
        // "Select photos" access covers photos and videos the user picked. Android also reports
        // it as granted next to full access, so it only counts when neither full permission is.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !images && !videos &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }

    /**
     * Reads every item of [type] the app can see. Returns null when the query itself fails, so
     * callers can tell "nothing there" apart from "couldn't read the gallery".
     */
    fun queryAll(type: MediaType): List<MediaItem>? {
        val isVideo = type == MediaType.VIDEO
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.ORIENTATION)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            if (isVideo) add(MediaStore.MediaColumns.DURATION)
        }.toTypedArray()
        val cursor = context.contentResolver.query(collection(type), projection, null, null, null)
            ?: return null
        return cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val orientationCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val durationCol = if (isVideo) c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION) else -1

            val items = ArrayList<MediaItem>(c.count)
            while (c.moveToNext()) {
                val modifiedSeconds = c.getLong(modifiedCol)
                val dateTaken = c.getLong(takenCol).takeIf { it > 0 }
                    ?: c.getLong(addedCol).takeIf { it > 0 }?.times(1000)
                    ?: (modifiedSeconds * 1000)
                // Store the dimensions as the item is displayed, not as encoded.
                val rotated = c.getInt(orientationCol).let { it == 90 || it == 270 }
                val width = c.getInt(widthCol)
                val height = c.getInt(heightCol)
                items += MediaItem(
                    id = c.getLong(idCol),
                    mediaType = type,
                    displayName = c.getString(nameCol).orEmpty(),
                    mimeType = c.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*",
                    dateTaken = dateTaken,
                    dateModified = modifiedSeconds,
                    size = c.getLong(sizeCol),
                    width = if (rotated) height else width,
                    height = if (rotated) width else height,
                    bucketName = c.getString(bucketCol),
                    relativePath = c.getString(pathCol),
                    durationMs = if (isVideo) c.getLong(durationCol).takeIf { it > 0 } else null,
                )
            }
            items
        }
    }

    /** Which of [ids] still exist in MediaStore, as photos or videos. */
    fun existingIds(ids: Collection<Long>): Set<Long> {
        val found = HashSet<Long>(ids.size)
        for (collection in listOf(images, videos)) {
            ids.filterNot(found::contains).chunked(SQL_CHUNK).forEach { chunk ->
                val selection = "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",") { "?" }})"
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    selection,
                    chunk.map(Long::toString).toTypedArray(),
                    null,
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    while (c.moveToNext()) found += c.getLong(idCol)
                }
            }
        }
        return found
    }
}
