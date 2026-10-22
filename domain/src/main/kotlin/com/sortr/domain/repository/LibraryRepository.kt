package com.sortr.domain.repository

import com.sortr.domain.model.Album
import com.sortr.domain.model.AnalysisProgress
import com.sortr.domain.model.CategoryCount
import com.sortr.domain.model.DateBucket
import com.sortr.domain.model.DuplicateGroup
import com.sortr.domain.model.LibraryStats
import com.sortr.domain.model.MoveResult
import com.sortr.domain.model.MovedCopy
import com.sortr.domain.model.Photo
import com.sortr.domain.model.QuickCounts
import com.sortr.domain.model.ScreenshotSummary
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.model.SourceCount
import kotlinx.coroutines.flow.Flow

/**
 * Ways of browsing the library: albums, dates, sources, duplicates and stats. Photos in the
 * vault or the Trash are left out everywhere.
 */
interface LibraryRepository {

    fun observeAlbums(): Flow<List<Album>>

    fun observeAlbumPhotos(relativePath: String): Flow<List<Photo>>

    /** One entry per month that has photos, newest first. */
    fun observeDateBuckets(): Flow<List<DateBucket>>

    /** Sources that have at least one photo. */
    fun observeSources(): Flow<List<SourceCount>>

    fun observeQuickCounts(): Flow<QuickCounts>

    fun observeStats(): Flow<LibraryStats>

    /** Photos checked for duplicates and blur so far. */
    fun observeAnalysisProgress(): Flow<AnalysisProgress>

    /** Groups of near-identical photos, biggest saving first. */
    suspend fun duplicateGroups(): List<DuplicateGroup>

    /** Groups of byte-identical photos and videos, biggest saving first. */
    suspend fun exactDuplicateGroups(): List<DuplicateGroup>

    /** Files still to be checked for exact copies. */
    fun observeExactCheckPending(): Flow<Int>

    /** Photos and videos a session with [filter] would show. */
    suspend fun countToReview(filter: SessionFilter): Int

    /** Photos read for categories and text so far. */
    fun observeSmartProgress(): Flow<AnalysisProgress>

    /** Categories with at least one photo. */
    fun observeCategories(): Flow<List<CategoryCount>>

    /** Photos with text matching what the user typed, newest first. */
    fun searchText(input: String): Flow<List<Photo>>

    /** Screenshots per folder, including ones saved outside screenshot folders. */
    fun observeScreenshots(): Flow<ScreenshotSummary>

    /** Screenshots that aren't in [album], for tidying them all into it. */
    suspend fun screenshotsOutside(album: String): List<Photo>

    /**
     * Moves photos into [relativePath]. Call only after the user granted write access in the
     * system dialog.
     */
    suspend fun movePhotos(photos: Collection<Photo>, relativePath: String): MoveResult

    /**
     * After the user was asked to remove the originals of [copies]: keeps copies whose original
     * is gone, deletes the rest. Returns how many were kept.
     */
    suspend fun completeCopies(copies: Collection<MovedCopy>): Int
}
