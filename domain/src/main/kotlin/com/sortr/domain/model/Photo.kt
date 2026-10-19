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
)

enum class PhotoStatus {
    UNREVIEWED,
    KEPT,
    TRASHED,
    VAULTED,
    SKIPPED,
}
