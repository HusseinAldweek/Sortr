package com.sortr.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sortr.domain.model.PhotoStatus
import kotlinx.coroutines.flow.Flow

/** Callers keep id lists under SQLite's 999-variable limit; see [SQL_CHUNK]. */
@Dao
interface PhotoDao {

    @Query("SELECT mediaStoreId, dateModified, size FROM photos")
    suspend fun syncKeys(): List<PhotoSyncKey>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Update(entity = PhotoEntity::class)
    suspend fun updateMetadata(updates: List<PhotoMetadataUpdate>)

    @Query("DELETE FROM photos WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM photos WHERE status = :status ORDER BY dateTaken DESC, mediaStoreId DESC")
    suspend fun byStatusNewestFirst(status: PhotoStatus): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE mediaStoreId = :id")
    suspend fun byId(id: Long): PhotoEntity?

    /** Photos swiped into the vault whose encryption never finished, e.g. the app was killed. */
    @Query(
        "SELECT * FROM photos WHERE status = 'VAULTED' AND mediaStoreId NOT IN " +
            "(SELECT sourceMediaStoreId FROM vault_items WHERE sourceMediaStoreId IS NOT NULL)",
    )
    suspend fun vaultedWithoutCopy(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE status = :status ORDER BY statusChangedAt DESC, mediaStoreId DESC")
    fun observeByStatusRecentlyChanged(status: PhotoStatus): Flow<List<PhotoEntity>>

    @Query("UPDATE photos SET status = :status, statusChangedAt = :changedAt WHERE mediaStoreId = :id")
    suspend fun updateStatus(id: Long, status: PhotoStatus, changedAt: Long)

    @Query("UPDATE photos SET status = :status, statusChangedAt = :changedAt WHERE mediaStoreId IN (:ids)")
    suspend fun updateStatuses(ids: List<Long>, status: PhotoStatus, changedAt: Long)

    @Query(
        "UPDATE photos SET status = 'UNREVIEWED', statusChangedAt = :changedAt " +
            "WHERE status IN ('KEPT', 'SKIPPED')",
    )
    suspend fun resetReviewed(changedAt: Long)

    @Query(
        """
        SELECT
            COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN status = 'UNREVIEWED' THEN 1 ELSE 0 END), 0) AS unreviewed,
            COALESCE(SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END), 0) AS skipped,
            COALESCE(SUM(CASE WHEN status = 'KEPT' THEN 1 ELSE 0 END), 0) AS kept,
            COALESCE(SUM(CASE WHEN status = 'TRASHED' THEN 1 ELSE 0 END), 0) AS trashedCount,
            COALESCE(SUM(CASE WHEN status = 'TRASHED' THEN size ELSE 0 END), 0) AS trashedBytes
        FROM photos
        """,
    )
    fun observeSummary(): Flow<SummaryRow>
}

const val SQL_CHUNK = 500

data class PhotoSyncKey(
    val mediaStoreId: Long,
    val dateModified: Long,
    val size: Long,
)

/** Partial update that leaves status and indexing columns alone. */
data class PhotoMetadataUpdate(
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val bucketName: String?,
    val relativePath: String?,
    val dateModified: Long,
)

data class SummaryRow(
    val total: Int,
    val unreviewed: Int,
    val skipped: Int,
    val kept: Int,
    val trashedCount: Int,
    val trashedBytes: Long,
)
