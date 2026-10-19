package com.sortr.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.MediaType
import com.sortr.domain.repository.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A photo or video found in a folder Android's gallery doesn't show. */
data class FolderItem(
    /** Stable id derived from the file's location; always negative so it never meets a MediaStore id. */
    val id: Long,
    val uri: Uri,
    val mediaType: MediaType,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    /** Epoch millis. */
    val lastModified: Long,
    val relativePath: String,
    val bucketName: String,
)

/** A folder the user let Sortr read, e.g. WhatsApp's media folder. */
data class HiddenFolder(val treeUri: String, val app: HiddenFolderApp)

enum class HiddenFolderApp { WHATSAPP, WHATSAPP_BUSINESS }

/**
 * WhatsApp keeps sent photos, chats with "Media visibility" turned off and similar media in
 * folders marked with a `.nomedia` file, so no gallery app (and no MediaStore query) sees them.
 * With the user's one-time permission to WhatsApp's media folder (picked in Android's own folder
 * picker), Sortr reads those files directly. Files the gallery already shows are skipped here
 * because the normal gallery sync has them.
 */
@Singleton
class HiddenFolders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettings,
) {
    val folders: Flow<List<HiddenFolder>> = settings.hiddenFolders.map { uris ->
        uris.mapNotNull { uri -> appOf(Uri.parse(uri))?.let { HiddenFolder(uri, it) } }.sortedBy { it.app }
    }

    /** Where the folder picker should open: the app's media folder on internal storage. */
    fun initialUri(app: HiddenFolderApp): Uri =
        DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE, "primary:" + mediaPathOf(app))

    /**
     * Keeps the permission for a folder the user picked. Returns false, without keeping anything,
     * when the folder isn't one of WhatsApp's. A gallery sync then picks up its items.
     */
    suspend fun add(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (appOf(treeUri) == null) {
            runCatching { context.contentResolver.releasePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            return@withContext false
        }
        // Delete access too, so trashed hidden files can be deleted after the user confirms.
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(treeUri, readWrite) }
            .onFailure { context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        settings.addHiddenFolder(treeUri.toString())
        true
    }

    /** Stops reading a folder; its items leave Sortr on the next gallery sync. */
    suspend fun remove(treeUri: String) = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(treeUri)
            val held = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
            val flags = (if (held?.isReadPermission == true) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (held?.isWritePermission == true) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            if (flags != 0) context.contentResolver.releasePersistableUriPermission(uri, flags)
        }
        settings.removeHiddenFolder(treeUri)
    }

    /** Folders whose permission is still held. Android drops it if WhatsApp is reinstalled. */
    suspend fun grantedFolders(): List<Uri> {
        val held = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
        return settings.hiddenFolders.first().map(Uri::parse).filter { it in held }
    }

    /**
     * Every hidden photo and video under [treeUri]. Returns null if the folder couldn't be read,
     * so callers don't mistake a failure for "everything was deleted".
     */
    fun scan(treeUri: Uri): List<FolderItem>? = try {
        val items = ArrayList<FolderItem>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        // A folder picked inside "WhatsApp Images" (say "Sent") is read as a hidden one.
        val segments = rootId.substringAfter(':').split('/')
        val mediaIndex = segments.indexOfFirst(::isMediaFolder)
        walk(treeUri, rootId, inMedia = mediaIndex >= 0, hidden = mediaIndex in 0 until segments.lastIndex, items)
        items
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read $treeUri", e)
        null
    }

    private fun walk(treeUri: Uri, dirId: String, inMedia: Boolean, hidden: Boolean, out: MutableList<FolderItem>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val entries = ArrayList<Entry>()
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                entries += Entry(
                    id = c.getString(0),
                    name = c.getString(1).orEmpty(),
                    mime = c.getString(2).orEmpty(),
                    size = if (c.isNull(3)) 0 else c.getLong(3),
                    modified = if (c.isNull(4)) 0 else c.getLong(4),
                )
            }
        } ?: throw IllegalStateException("No listing for $dirId")

        // The same rule MediaStore uses: a ".nomedia" file or a name starting with "." hides a
        // folder and everything below it.
        val hiddenHere = hidden || entries.any { it.name == NO_MEDIA }
        for (entry in entries) {
            if (entry.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                val isMediaFolder = isMediaFolder(entry.name)
                when {
                    inMedia -> walk(treeUri, entry.id, inMedia = true, hidden = hiddenHere || entry.name.startsWith('.'), out)
                    isMediaFolder -> walk(treeUri, entry.id, inMedia = true, hidden = entry.name.startsWith('.'), out)
                    // Only find the way down to the media folders; skip statuses, documents,
                    // audio, stickers, databases and backups.
                    entry.name in PATH_FOLDERS -> walk(treeUri, entry.id, inMedia = false, hidden = false, out)
                }
            } else if (inMedia && hiddenHere) {
                val type = when {
                    entry.mime.startsWith("image/") -> MediaType.IMAGE
                    entry.mime.startsWith("video/") -> MediaType.VIDEO
                    else -> null
                } ?: continue
                val path = entry.id.substringAfter(':')
                val relativePath = path.substringBeforeLast('/', "") + "/"
                out += FolderItem(
                    id = idFor(entry.id),
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.id),
                    mediaType = type,
                    displayName = entry.name,
                    mimeType = entry.mime,
                    size = entry.size,
                    lastModified = entry.modified,
                    relativePath = relativePath,
                    bucketName = bucketNameFor(relativePath),
                )
            }
        }
    }

    private class Entry(val id: String, val name: String, val mime: String, val size: Long, val modified: Long)

    companion object {
        private const val TAG = "HiddenFolders"
        private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
        private const val NO_MEDIA = ".nomedia"

        /** Folders on the way from a picked folder down to the media folders. */
        private val PATH_FOLDERS = setOf("Android", "media", "com.whatsapp", "com.whatsapp.w4b", "WhatsApp", "WhatsApp Business", "Media")

        fun isMediaFolder(name: String) = AlbumPaths.isWhatsAppMediaFolder(name)

        fun mediaPathOf(app: HiddenFolderApp) = when (app) {
            HiddenFolderApp.WHATSAPP -> "Android/media/com.whatsapp/WhatsApp/Media"
            HiddenFolderApp.WHATSAPP_BUSINESS -> "Android/media/com.whatsapp.w4b/WhatsApp Business/Media"
        }

        /** Which app a picked folder belongs to, or null if it isn't one of WhatsApp's. */
        fun appOf(treeUri: Uri): HiddenFolderApp? {
            if (treeUri.authority != EXTERNAL_STORAGE) return null
            val path = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()?.substringAfter(':') ?: return null
            return when {
                path.startsWith("Android/media/com.whatsapp.w4b") || path.startsWith("WhatsApp Business") -> HiddenFolderApp.WHATSAPP_BUSINESS
                path.startsWith("Android/media/com.whatsapp") || path == "WhatsApp" || path.startsWith("WhatsApp/") -> HiddenFolderApp.WHATSAPP
                else -> null
            }
        }

        /** "WhatsApp Images · Sent" for a file in "…/WhatsApp Images/Sent/". */
        fun bucketNameFor(relativePath: String): String = AlbumPaths.displayName(relativePath)

        /**
         * A stable negative id from the file's location (64-bit FNV-1a), so the same file keeps its
         * review status between scans.
         */
        fun idFor(documentId: String): Long {
            var hash = -0x340d631b7bdddcdbL // FNV offset basis 0xcbf29ce484222325
            for (byte in documentId.toByteArray(Charsets.UTF_8)) {
                hash = hash xor (byte.toLong() and 0xff)
                hash *= 0x100000001b3L
            }
            return -(hash ushr 1) - 1
        }
    }
}
