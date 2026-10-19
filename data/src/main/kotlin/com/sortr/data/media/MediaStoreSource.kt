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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One row of the MediaStore images table. */
data class MediaImage(
    val id: Long,
    val displayName: String,
    val mimeType: String,
    val dateTaken: Long,
    val dateModified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val bucketName: String?,
    val relativePath: String?,
)

@Singleton
class MediaStoreSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    fun uriFor(id: Long): Uri = ContentUris.withAppendedId(collection, id)

    /** True when the app may read at least some of the user's photos. */
    fun hasReadAccess(): Boolean {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Reads every image the app can see. Returns null when the query itself fails, so callers
     * can tell "no photos" apart from "couldn't read the gallery".
     */
    fun queryAllImages(): List<MediaImage>? {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val cursor = context.contentResolver.query(collection, projection, null, null, null)
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

            val images = ArrayList<MediaImage>(c.count)
            while (c.moveToNext()) {
                val modifiedSeconds = c.getLong(modifiedCol)
                val dateTaken = c.getLong(takenCol).takeIf { it > 0 }
                    ?: c.getLong(addedCol).takeIf { it > 0 }?.times(1000)
                    ?: (modifiedSeconds * 1000)
                // Store the dimensions as the photo is displayed, not as encoded.
                val rotated = c.getInt(orientationCol).let { it == 90 || it == 270 }
                val width = c.getInt(widthCol)
                val height = c.getInt(heightCol)
                images += MediaImage(
                    id = c.getLong(idCol),
                    displayName = c.getString(nameCol).orEmpty(),
                    mimeType = c.getString(mimeCol) ?: "image/*",
                    dateTaken = dateTaken,
                    dateModified = modifiedSeconds,
                    size = c.getLong(sizeCol),
                    width = if (rotated) height else width,
                    height = if (rotated) width else height,
                    bucketName = c.getString(bucketCol),
                    relativePath = c.getString(pathCol),
                )
            }
            images
        }
    }

    /** Which of [ids] still exist in MediaStore. */
    fun existingIds(ids: Collection<Long>): Set<Long> {
        val found = HashSet<Long>(ids.size)
        ids.chunked(SQL_CHUNK).forEach { chunk ->
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
        return found
    }
}
