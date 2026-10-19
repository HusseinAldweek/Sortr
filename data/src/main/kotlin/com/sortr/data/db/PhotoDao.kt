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

    @Query(
        "SELECT * FROM photos WHERE status = :status AND mediaStoreId IN " +
            "(SELECT photoId FROM faces WHERE personId = :personId) ORDER BY dateTaken DESC, mediaStoreId DESC",
    )
    suspend fun byStatusWithPerson(status: PhotoStatus, personId: Long): List<PhotoEntity>

    @Query(
        "SELECT * FROM photos WHERE status = :status AND mediaStoreId IN " +
            "(SELECT f.photoId FROM faces f WHERE $IS_UNSORTED_FACE) ORDER BY dateTaken DESC, mediaStoreId DESC",
    )
    suspend fun byStatusWithUnsortedFaces(status: PhotoStatus): List<PhotoEntity>

    /** Photos still to be scanned for faces, newest first. Trashed and vaulted ones are skipped. */
    // "indexed" is an SQLite keyword, so the column is always quoted.

    @Query("SELECT * FROM photos WHERE `indexed` = 0 AND $SCANNABLE ORDER BY dateTaken DESC, mediaStoreId DESC LIMIT :limit")
    suspend fun unindexed(limit: Int): List<PhotoEntity>

    @Query("SELECT mediaStoreId FROM photos WHERE mediaStoreId IN (:ids) AND $SCANNABLE")
    suspend fun stillScannable(ids: List<Long>): List<Long>

    @Query("UPDATE photos SET `indexed` = 1 WHERE mediaStoreId IN (:ids)")
    suspend fun markIndexed(ids: List<Long>)

    @Query("UPDATE photos SET `indexed` = 0")
    suspend fun resetIndexed()

    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(`indexed`), 0) AS scanned FROM photos WHERE $SCANNABLE")
    fun observeIndexProgress(): Flow<IndexProgressRow>

    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(`indexed`), 0) AS scanned FROM photos WHERE $SCANNABLE")
    suspend fun indexProgress(): IndexProgressRow

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

/** Photos that get scanned for faces. */
private const val SCANNABLE = "status IN ('UNREVIEWED', 'KEPT', 'SKIPPED')"

data class IndexProgressRow(val total: Int, val scanned: Int)

data class PhotoSyncKey(
    val mediaStoreId: Long,
    val dateModified: Long,
    val size: Long,
)

/** Partial update for an edited photo: keeps its review status but marks it for a new face scan. */
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
    val indexed: Boolean = false,
)

data class SummaryRow(
    val total: Int,
    val unreviewed: Int,
    val skipped: Int,
    val kept: Int,
    val trashedCount: Int,
    val trashedBytes: Long,
)
