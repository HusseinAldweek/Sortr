package com.sortr.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/** Everything that isn't in the vault or the Trash. */
private const val IN_LIBRARY = "status NOT IN ('VAULTED', 'TRASHED')"
private const val TO_REVIEW = "status IN ('UNREVIEWED', 'SKIPPED')"

@Dao
interface LibraryDao {

    @Query(
        "SELECT p.relativePath AS relativePath, MAX(p.bucketName) AS bucketName, COUNT(*) AS count, " +
            "COALESCE(SUM(p.size), 0) AS sizeBytes, SUM(CASE WHEN p.$TO_REVIEW THEN 1 ELSE 0 END) AS toReview, " +
            "MAX(p.dateTaken) AS newest, " +
            "(SELECT c.uri FROM photos c WHERE c.relativePath = p.relativePath AND c.$IN_LIBRARY " +
            "ORDER BY c.dateTaken DESC LIMIT 1) AS coverUri " +
            "FROM photos p WHERE p.$IN_LIBRARY AND p.relativePath IS NOT NULL GROUP BY p.relativePath",
    )
    fun observeAlbums(): Flow<List<AlbumRow>>

    @Query("SELECT * FROM photos WHERE relativePath = :relativePath AND $IN_LIBRARY ORDER BY dateTaken DESC, mediaStoreId DESC")
    fun observeAlbumPhotos(relativePath: String): Flow<List<PhotoEntity>>

    @Query(
        "SELECT CAST(strftime('%Y', dateTaken / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year, " +
            "CAST(strftime('%m', dateTaken / 1000, 'unixepoch', 'localtime') AS INTEGER) AS month, " +
            "COUNT(*) AS count, COALESCE(SUM(size), 0) AS sizeBytes, " +
            "SUM(CASE WHEN $TO_REVIEW THEN 1 ELSE 0 END) AS toReview " +
            "FROM photos WHERE $IN_LIBRARY GROUP BY year, month ORDER BY year DESC, month DESC",
    )
    fun observeDateBuckets(): Flow<List<DateBucketRow>>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN mediaType = 'VIDEO' AND $TO_REVIEW THEN 1 ELSE 0 END), 0) AS videosToReview, " +
            "COALESCE(SUM(CASE WHEN mediaType = 'VIDEO' THEN 1 ELSE 0 END), 0) AS videos, " +
            "COALESCE(SUM(CASE WHEN mediaType = 'IMAGE' AND blurScore >= 0 AND blurScore < :blurThreshold AND $TO_REVIEW " +
            "THEN 1 ELSE 0 END), 0) AS blurryToReview, " +
            "COALESCE(SUM(CASE WHEN size >= :largeBytes AND $TO_REVIEW THEN 1 ELSE 0 END), 0) AS largeToReview " +
            "FROM photos WHERE $IN_LIBRARY",
    )
    fun observeQuickCounts(blurThreshold: Double, largeBytes: Long): Flow<QuickCountsRow>

    @Query(
        "SELECT (SELECT COUNT(*) FROM actions WHERE action = 'KEEP') AS kept, " +
            "(SELECT COUNT(*) FROM actions WHERE action = 'DELETE') AS movedToTrash, " +
            "(SELECT COUNT(*) FROM actions WHERE action = 'PURGE') AS deletedFromPhone, " +
            "(SELECT COALESCE(SUM(sizeBytes), 0) FROM actions WHERE action = 'PURGE') AS freedBytes, " +
            "(SELECT COUNT(*) FROM actions WHERE action = 'PRIVATE') AS movedToVault, " +
            "(SELECT COUNT(*) FROM actions WHERE action = 'SKIP') AS skipped, " +
            "(SELECT COUNT(*) FROM vault_items) AS inVault",
    )
    fun observeStats(): Flow<StatsRow>

    @Query(
        "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN blurScore IS NOT NULL THEN 1 ELSE 0 END), 0) AS analysed " +
            "FROM photos WHERE mediaType = 'IMAGE' AND $IN_LIBRARY",
    )
    fun observeAnalysisProgress(): Flow<AnalysisRow>

    @Query(
        "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN blurScore IS NOT NULL THEN 1 ELSE 0 END), 0) AS analysed " +
            "FROM photos WHERE mediaType = 'IMAGE' AND $IN_LIBRARY",
    )
    suspend fun analysisProgress(): AnalysisRow

    /** Photos still to be checked for duplicates and blur, newest first. */
    @Query("SELECT * FROM photos WHERE blurScore IS NULL AND mediaType = 'IMAGE' AND $IN_LIBRARY ORDER BY dateTaken DESC LIMIT :limit")
    suspend fun unanalysed(limit: Int): List<PhotoEntity>

    @Query("UPDATE photos SET dHash = :hash, blurScore = :blurScore WHERE mediaStoreId = :id")
    suspend fun setAnalysis(id: Long, hash: Long?, blurScore: Double)

    @Query("UPDATE photos SET width = :width, height = :height WHERE mediaStoreId = :id")
    suspend fun setDimensions(id: Long, width: Int, height: Int)

    @Query("UPDATE photos SET dateTaken = :dateTaken WHERE mediaStoreId = :id")
    suspend fun setDateTaken(id: Long, dateTaken: Long)

    /** Just ids and hashes, so grouping a big library stays light. */
    @Query("SELECT mediaStoreId, dHash FROM photos WHERE dHash IS NOT NULL AND mediaType = 'IMAGE' AND $IN_LIBRARY")
    suspend fun hashes(): List<HashRow>

    @Query("SELECT * FROM photos WHERE mediaStoreId IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<PhotoEntity>

    @RawQuery
    suspend fun photos(query: SupportSQLiteQuery): List<PhotoEntity>

    @RawQuery
    suspend fun count(query: SupportSQLiteQuery): Int

    @RawQuery(observedEntities = [PhotoEntity::class])
    fun observeScreenshotFolders(query: SupportSQLiteQuery): Flow<List<ScreenshotFolderRow>>

    /** Files without a fingerprint whose size matches another file's: the only ones that can be exact copies. */
    @Query("SELECT * FROM photos WHERE contentHash IS NULL AND $SAME_SIZE ORDER BY size LIMIT :limit")
    suspend fun unfingerprinted(limit: Int): List<PhotoEntity>

    @Query("SELECT COUNT(*) FROM photos WHERE contentHash IS NULL AND $SAME_SIZE")
    fun observeUnfingerprinted(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE contentHash IS NULL AND $SAME_SIZE")
    suspend fun unfingerprintedCount(): Int

    @Query("UPDATE photos SET contentHash = :hash WHERE mediaStoreId = :id")
    suspend fun setContentHash(id: Long, hash: String)

    /** Photos not read for categories and text yet, newest first. */
    @Query("SELECT * FROM photos WHERE smartCategories IS NULL AND mediaType = 'IMAGE' AND $IN_LIBRARY ORDER BY dateTaken DESC LIMIT :limit")
    suspend fun unclassified(limit: Int): List<PhotoEntity>

    @Query("UPDATE photos SET smartCategories = :mask WHERE mediaStoreId = :id")
    suspend fun setSmartCategories(id: Long, mask: Int)

    @Query(
        "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN smartCategories IS NOT NULL THEN 1 ELSE 0 END), 0) AS analysed " +
            "FROM photos WHERE mediaType = 'IMAGE' AND $IN_LIBRARY",
    )
    fun observeSmartProgress(): Flow<AnalysisRow>

    @Query(
        "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN smartCategories IS NOT NULL THEN 1 ELSE 0 END), 0) AS analysed " +
            "FROM photos WHERE mediaType = 'IMAGE' AND $IN_LIBRARY",
    )
    suspend fun smartProgress(): AnalysisRow

    /** Photos per category mask, split by whether they're still to be sorted. */
    @Query(
        "SELECT smartCategories AS mask, COUNT(*) AS count, SUM(CASE WHEN $TO_REVIEW THEN 1 ELSE 0 END) AS toReview " +
            "FROM photos WHERE smartCategories > 0 AND $IN_LIBRARY GROUP BY smartCategories",
    )
    fun observeCategoryMasks(): Flow<List<CategoryMaskRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertText(row: PhotoTextEntity)

    @Query("DELETE FROM photo_text WHERE rowid = :photoId")
    suspend fun deleteText(photoId: Long)

    /** Text of photos that are gone or in the vault. Photos in the Trash keep theirs in case they come back. */
    @Query("DELETE FROM photo_text WHERE rowid NOT IN (SELECT mediaStoreId FROM photos WHERE status != 'VAULTED')")
    suspend fun deleteOrphanText()

    /** Photos whose text matches an FTS query, newest first. */
    @Query(
        "SELECT * FROM photos WHERE $IN_LIBRARY AND mediaStoreId IN (SELECT rowid FROM photo_text WHERE photo_text MATCH :query) " +
            "ORDER BY dateTaken DESC, mediaStoreId DESC LIMIT :limit",
    )
    fun observeTextSearch(query: String, limit: Int): Flow<List<PhotoEntity>>

    /** Fingerprinted files outside the Trash and the vault. */
    @Query("SELECT mediaStoreId, size, contentHash AS hash FROM photos WHERE contentHash IS NOT NULL AND contentHash != '' AND $IN_LIBRARY")
    suspend fun fingerprints(): List<FingerprintRow>
}

/** In the library, with a size, and not the only file of that exact size. */
private const val SAME_SIZE = "$IN_LIBRARY AND size > 0 AND size IN " +
    "(SELECT size FROM photos WHERE $IN_LIBRARY AND size > 0 GROUP BY size HAVING COUNT(*) > 1)"

data class FingerprintRow(val mediaStoreId: Long, val size: Long, val hash: String)

data class CategoryMaskRow(val mask: Int, val count: Int, val toReview: Int)

data class ScreenshotFolderRow(val relativePath: String, val count: Int, val sizeBytes: Long, val toReview: Int, val oldToReview: Int)

data class AlbumRow(
    val relativePath: String,
    val bucketName: String?,
    val count: Int,
    val sizeBytes: Long,
    val toReview: Int,
    val newest: Long,
    val coverUri: String?,
)

data class DateBucketRow(val year: Int, val month: Int, val count: Int, val sizeBytes: Long, val toReview: Int)

data class QuickCountsRow(val videosToReview: Int, val videos: Int, val blurryToReview: Int, val largeToReview: Int)

data class StatsRow(
    val kept: Int,
    val movedToTrash: Int,
    val deletedFromPhone: Int,
    val freedBytes: Long,
    val movedToVault: Int,
    val skipped: Int,
    val inVault: Int,
)

data class AnalysisRow(val total: Int, val analysed: Int)

data class HashRow(val mediaStoreId: Long, val dHash: Long)
