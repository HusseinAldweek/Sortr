package com.sortr.data.repository

import com.sortr.data.db.SessionQueries
import com.sortr.data.db.SortrDatabase
import com.sortr.data.db.toDomain
import com.sortr.data.media.MediaMover
import com.sortr.domain.analysis.BlurMeter
import com.sortr.domain.model.Album
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.AnalysisProgress
import com.sortr.domain.model.DateBucket
import com.sortr.domain.model.DuplicateGroup
import com.sortr.domain.model.LibraryStats
import com.sortr.domain.model.MediaSource
import com.sortr.domain.model.MoveResult
import com.sortr.domain.model.MovedCopy
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.model.QuickCounts
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.model.SourceCount
import com.sortr.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    db: SortrDatabase,
    private val duplicates: DuplicateIndex,
    private val mover: MediaMover,
) : LibraryRepository {

    private val libraryDao = db.libraryDao()

    override fun observeAlbums(): Flow<List<Album>> = libraryDao.observeAlbums().map { rows ->
        rows.map {
            Album(
                relativePath = it.relativePath,
                name = it.bucketName?.takeIf(String::isNotBlank) ?: AlbumPaths.displayName(it.relativePath),
                count = it.count,
                sizeBytes = it.sizeBytes,
                toReview = it.toReview,
                newest = it.newest,
                coverUri = it.coverUri,
            )
        }
    }.distinctUntilChanged()

    override fun observeAlbumPhotos(relativePath: String): Flow<List<Photo>> =
        libraryDao.observeAlbumPhotos(relativePath).map { rows -> rows.map { it.toDomain() } }

    override fun observeDateBuckets(): Flow<List<DateBucket>> = libraryDao.observeDateBuckets().map { rows ->
        rows.map { DateBucket(year = it.year, month = it.month, count = it.count, sizeBytes = it.sizeBytes, toReview = it.toReview) }
    }.distinctUntilChanged()

    /** Worked out from the album list: a source is a set of folder patterns. */
    override fun observeSources(): Flow<List<SourceCount>> = observeAlbums().map { albums ->
        MediaSource.entries.mapNotNull { source ->
            val matching = albums.filter { source.matches(it.relativePath) }
            if (matching.isEmpty()) {
                null
            } else {
                SourceCount(source, count = matching.sumOf { it.count }, toReview = matching.sumOf { it.toReview })
            }
        }
    }.distinctUntilChanged()

    override fun observeQuickCounts(): Flow<QuickCounts> = combine(
        libraryDao.observeQuickCounts(BlurMeter.BLURRY_THRESHOLD, SessionFilter.LARGE_FILE_BYTES),
        observeAlbums(),
        observeDateBuckets(),
    ) { counts, albums, buckets ->
        QuickCounts(
            videosToReview = counts.videosToReview,
            videos = counts.videos,
            blurryToReview = counts.blurryToReview,
            largeToReview = counts.largeToReview,
            albums = albums.size,
            years = buckets.map { it.year }.distinct().size,
        )
    }.distinctUntilChanged()

    override fun observeStats(): Flow<LibraryStats> = libraryDao.observeStats().map {
        LibraryStats(
            kept = it.kept,
            movedToTrash = it.movedToTrash,
            deletedFromPhone = it.deletedFromPhone,
            freedBytes = it.freedBytes,
            movedToVault = it.movedToVault,
            skipped = it.skipped,
            inVault = it.inVault,
        )
    }.distinctUntilChanged()

    override fun observeAnalysisProgress(): Flow<AnalysisProgress> =
        libraryDao.observeAnalysisProgress().map { AnalysisProgress(analysed = it.analysed, total = it.total) }.distinctUntilChanged()

    override suspend fun duplicateGroups(): List<DuplicateGroup> = duplicates.groups()

    override suspend fun countToReview(filter: SessionFilter): Int =
        if (filter == SessionFilter.Duplicates) {
            duplicates.groups().sumOf { group ->
                group.photos.count { it.id != group.bestId && it.status in TO_REVIEW }
            }
        } else {
            libraryDao.count(SessionQueries.countToReview(filter))
        }

    override suspend fun movePhotos(photos: Collection<Photo>, relativePath: String): MoveResult =
        mover.move(photos, relativePath)

    override suspend fun completeCopies(copies: Collection<MovedCopy>): Int = mover.completeCopies(copies)

    private companion object {
        val TO_REVIEW = setOf(PhotoStatus.UNREVIEWED, PhotoStatus.SKIPPED)
    }
}
