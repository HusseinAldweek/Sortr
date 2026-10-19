package com.sortr.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sortr.domain.model.ActionType
import com.sortr.domain.model.PhotoStatus

@Entity(
    tableName = "photos",
    indices = [Index("status"), Index("dateTaken")],
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
    val indexed: Boolean = false,
    val dHash: Long? = null,
    val blurScore: Double? = null,
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
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val embedding: ByteArray,
    val personId: Long?,
    val confidence: Float,
)

@Entity(tableName = "people")
class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val averageEmbedding: ByteArray,
    val coverFaceId: Long?,
    val hidden: Boolean = false,
    val faceCount: Int = 0,
)

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
