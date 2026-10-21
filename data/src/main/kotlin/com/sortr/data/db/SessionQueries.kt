package com.sortr.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import com.sortr.domain.analysis.BlurMeter
import com.sortr.domain.model.DateRanges
import com.sortr.domain.model.MediaTypeFilter
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.model.ScreenshotRules
import com.sortr.domain.model.SessionFilter
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Builds the SQL for each kind of swipe session. Duplicates aren't expressible in SQL and are
 * handled by [com.sortr.data.repository.DuplicateIndex].
 */
internal object SessionQueries {

    private const val NEWEST_FIRST = "dateTaken DESC, mediaStoreId DESC"
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Screenshots, by folder or file name. Images only: screen recordings are a separate source. */
    private val SCREENSHOT_SQL = "mediaType = 'IMAGE' AND (" +
        (ScreenshotRules.FOLDER_PATTERNS.map { "relativePath LIKE ?" } + ScreenshotRules.NAME_PATTERNS.map { "displayName LIKE ?" })
            .joinToString(" OR ") + ")"
    private val SCREENSHOT_ARGS: List<Any> = ScreenshotRules.FOLDER_PATTERNS + ScreenshotRules.NAME_PATTERNS

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

    /** Screenshots per folder, outside the Trash and the vault. [oldBefore] marks the older ones. */
    fun screenshotFolders(oldBefore: Long): SimpleSQLiteQuery = SimpleSQLiteQuery(
        "SELECT COALESCE(relativePath, '') AS relativePath, COUNT(*) AS count, COALESCE(SUM(size), 0) AS sizeBytes, " +
            "SUM(status IN ('UNREVIEWED', 'SKIPPED')) AS toReview, " +
            "SUM(status IN ('UNREVIEWED', 'SKIPPED') AND dateTaken > 0 AND dateTaken < ?) AS oldToReview " +
            "FROM photos WHERE status NOT IN ('TRASHED', 'VAULTED') AND ($SCREENSHOT_SQL) GROUP BY relativePath",
        (listOf<Any>(oldBefore) + SCREENSHOT_ARGS).toTypedArray(),
    )

    /** Screenshots outside the Trash and the vault that aren't in [album]. */
    fun screenshotsOutside(album: String): SimpleSQLiteQuery = SimpleSQLiteQuery(
        "SELECT * FROM photos WHERE status NOT IN ('TRASHED', 'VAULTED') AND ($SCREENSHOT_SQL) " +
            "AND COALESCE(relativePath, '') != ? ORDER BY $NEWEST_FIRST",
        (SCREENSHOT_ARGS + album).toTypedArray(),
    )

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
        is SessionFilter.Screenshots -> screenshots(filter.olderThanDays)
        SessionFilter.OnThisDay -> onThisDay()
    }

    private fun screenshots(olderThanDays: Int?): Clause {
        if (olderThanDays == null) return Clause(SCREENSHOT_SQL, SCREENSHOT_ARGS)
        val cutoff = System.currentTimeMillis() - olderThanDays * DAY_MS
        return Clause("$SCREENSHOT_SQL AND dateTaken > 0 AND dateTaken < ?", SCREENSHOT_ARGS + cutoff)
    }

    /** Same month and day as today, in an earlier year. */
    private fun onThisDay(): Clause {
        val today = LocalDate.now()
        val startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Clause(
            "dateTaken > 0 AND dateTaken < ? AND strftime('%m-%d', dateTaken / 1000, 'unixepoch', 'localtime') = ?",
            // Locale.ROOT: SQLite compares against ASCII digits, whatever the phone's language.
            listOf(startOfToday, String.format(Locale.ROOT, "%02d-%02d", today.monthValue, today.dayOfMonth)),
        )
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
