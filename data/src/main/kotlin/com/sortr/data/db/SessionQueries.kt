package com.sortr.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import com.sortr.domain.analysis.BlurMeter
import com.sortr.domain.model.DateRanges
import com.sortr.domain.model.MediaTypeFilter
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.model.SessionFilter

/**
 * Builds the SQL for each kind of swipe session. Duplicates aren't expressible in SQL and are
 * handled by [com.sortr.data.repository.DuplicateIndex].
 */
internal object SessionQueries {

    private const val NEWEST_FIRST = "dateTaken DESC, mediaStoreId DESC"

    private class Clause(val sql: String, val args: List<Any> = emptyList(), val order: String = NEWEST_FIRST)

    fun select(filter: SessionFilter, status: PhotoStatus): SimpleSQLiteQuery {
        val clause = clause(filter)
        return SimpleSQLiteQuery(
            "SELECT * FROM photos WHERE status = ? AND (${clause.sql}) ORDER BY ${clause.order}",
            (listOf<Any>(status.name) + clause.args).toTypedArray(),
        )
    }

    /** Photos a new session with [filter] would show: unreviewed and skipped. */
    fun countToReview(filter: SessionFilter): SimpleSQLiteQuery {
        val clause = clause(filter)
        return SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM photos WHERE status IN ('UNREVIEWED', 'SKIPPED') AND (${clause.sql})",
            clause.args.toTypedArray(),
        )
    }

    private fun clause(filter: SessionFilter): Clause = when (filter) {
        SessionFilter.AllPhotos -> Clause("1")
        is SessionFilter.Person -> Clause("mediaStoreId IN (SELECT photoId FROM faces WHERE personId = ?)", listOf(filter.personId))
        SessionFilter.UnsortedFaces -> Clause("mediaStoreId IN (SELECT f.photoId FROM faces f WHERE $IS_UNSORTED_FACE)")
        is SessionFilter.Year -> range(DateRanges.year(filter.year))
        is SessionFilter.Month -> range(DateRanges.month(filter.year, filter.month))
        is SessionFilter.Album -> Clause("relativePath = ?", listOf(filter.relativePath))
        is SessionFilter.Source -> Clause(filter.source.patterns.joinToString(" OR ") { "relativePath LIKE ?" }, filter.source.patterns)
        SessionFilter.Videos -> Clause("mediaType = 'VIDEO'")
        SessionFilter.Blurry -> Clause(
            "mediaType = 'IMAGE' AND blurScore >= 0 AND blurScore < ?",
            listOf(BlurMeter.BLURRY_THRESHOLD),
            order = "blurScore ASC, mediaStoreId DESC",
        )
        SessionFilter.Largest -> Clause("size >= ?", listOf(SessionFilter.LARGE_FILE_BYTES), order = "size DESC, mediaStoreId DESC")
        SessionFilter.Duplicates -> throw IllegalArgumentException("Duplicate sessions are built from the duplicate index")
        is SessionFilter.Custom -> custom(filter)
    }

    private fun custom(filter: SessionFilter.Custom): Clause {
        val parts = mutableListOf<String>()
        val args = mutableListOf<Any>()
        when (filter.mediaType) {
            MediaTypeFilter.ALL -> Unit
            MediaTypeFilter.PHOTOS -> parts += "mediaType = 'IMAGE'"
            MediaTypeFilter.VIDEOS -> parts += "mediaType = 'VIDEO'"
        }
        val year = filter.year
        val month = filter.month
        if (year != null) {
            val period = if (month != null) DateRanges.month(year, month) else DateRanges.year(year)
            parts += "dateTaken >= ? AND dateTaken <= ?"
            args += period.first
            args += period.last
        }
        if (filter.albums.isNotEmpty()) {
            parts += "relativePath IN (${filter.albums.joinToString(",") { "?" }})"
            args.addAll(filter.albums)
        }
        filter.minSizeBytes?.let {
            parts += "size >= ?"
            args += it
        }
        return Clause(parts.ifEmpty { listOf("1") }.joinToString(" AND "), args)
    }

    private fun range(period: LongRange) = Clause("dateTaken >= ? AND dateTaken <= ?", listOf(period.first, period.last))
}
