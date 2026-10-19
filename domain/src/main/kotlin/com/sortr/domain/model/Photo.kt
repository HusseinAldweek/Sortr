package com.sortr.domain.model

/** A gallery image, identified by its MediaStore `_ID`. */
data class Photo(
    val id: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    /** Epoch millis. Falls back to the file's modified time when the photo has no capture date. */
    val dateTaken: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val bucketName: String?,
    val relativePath: String?,
    val status: PhotoStatus,
    val mediaType: MediaType = MediaType.IMAGE,
    /** Length of a video; null for photos. */
    val durationMs: Long? = null,
    /** Sharpness score (see BlurMeter); null until analysed. */
    val blurScore: Double? = null,
    /**
     * Read from a folder the gallery hides (WhatsApp's sent media, say). Android's delete and
     * move dialogs don't cover these files.
     */
    val fromHiddenFolder: Boolean = false,
) {
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
}

enum class PhotoStatus {
    UNREVIEWED,
    KEPT,
    TRASHED,
    VAULTED,
    SKIPPED,
}
