package com.sortr.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sortr.domain.model.ActionType
import com.sortr.domain.model.MediaType
import com.sortr.domain.model.PhotoStatus

@Entity(
    tableName = "photos",
    indices = [Index("status"), Index("dateTaken"), Index("indexed"), Index("relativePath")],
)
data class PhotoEntity(
    @PrimaryKey val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val bucketName: String?,
    val relativePath: String?,
    /** MediaStore DATE_MODIFIED (seconds), used to spot edited files during sync. */
    val dateModified: Long,
    val status: PhotoStatus,
    val statusChangedAt: Long,
    /** Scanned for faces. */
    val indexed: Boolean = false,
    /** Perceptual hash for finding duplicates; null until analysed. */
    val dHash: Long? = null,
    /** Sharpness score; null until analysed, −1 if the photo couldn't be read. */
    val blurScore: Double? = null,
    @ColumnInfo(defaultValue = "'IMAGE'")
    val mediaType: MediaType = MediaType.IMAGE,
    /** Video length in milliseconds; null for photos. */
    @ColumnInfo(defaultValue = "NULL")
    val durationMs: Long? = null,
)

@Entity(
    tableName = "faces",
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["mediaStoreId"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("photoId"), Index("personId")],
)
class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoId: Long,
    /** Bounding box in units of 1/10,000 of the photo's displayed width and height. */
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** L2-normalised embedding, little-endian float32. */
    val embedding: ByteArray,
    val personId: Long?,
    /** Similarity to the person's average when the face was placed. */
    val confidence: Float,
    /** FaceQuality ordinal: 0 weak, 1 good. */
    @ColumnInfo(defaultValue = "0")
    val quality: Int = 0,
    /** The user said this face isn't this person. */
    @ColumnInfo(defaultValue = "NULL")
    val rejectedPersonId: Long? = null,
)

@Entity(tableName = "people")
class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    /** Plain average of the member embeddings (not re-normalised). */
    val averageEmbedding: ByteArray,
    /** Reserved for a user-chosen cover; screens use the person's largest clear face. */
    val coverFaceId: Long?,
    val hidden: Boolean = false,
    /** Members counted in [averageEmbedding]. */
    val faceCount: Int = 0,
    /** Named, merged, hidden or corrected by the user, so regrouping leaves it alone. */
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
)

/** BOX_SCALE units per photo width or height. */
const val BOX_SCALE = 10_000

@Entity(
    tableName = "vault_items",
    indices = [Index("sourceMediaStoreId"), Index("contentSha256")],
)
class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val encryptedFilePath: String,
    val thumbnailPath: String?,
    val originalName: String,
    val mimeType: String,
    val originalSize: Long,
    val dateTaken: Long,
    val iv: ByteArray,
    /** Per-file data key, encrypted with the Keystore master key. */
    val wrappedKey: ByteArray,
    val addedAt: Long,
    /** Gallery entry this was copied from, until the original has been removed. */
    val sourceMediaStoreId: Long?,
    val originalRemoved: Boolean = false,
    /** Hex SHA-256 of the plaintext, used to skip duplicates when importing a backup. */
    @ColumnInfo(defaultValue = "NULL")
    val contentSha256: String? = null,
    @ColumnInfo(defaultValue = "0")
    val width: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val height: Int = 0,
    /** Album the photo came from, so restoring can put it back there. Null for backup imports. */
    @ColumnInfo(defaultValue = "NULL")
    val originalRelativePath: String? = null,
)

/** Action log, used for undo and stats. No foreign key: rows outlive deleted photos. */
@Entity(
    tableName = "actions",
    indices = [Index("photoId"), Index("timestamp")],
)
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoId: Long,
    val action: ActionType,
    val previousStatus: PhotoStatus?,
    val sizeBytes: Long,
    val timestamp: Long,
)
