package com.sortr.domain.model

import com.sortr.domain.analysis.SmartCategory
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class MediaType { IMAGE, VIDEO }

enum class MediaTypeFilter { ALL, PHOTOS, VIDEOS }

/**
 * Apps and folders photos come from, recognised by their folder path. Patterns use SQL LIKE
 * syntax (`%` = anything) and are matched case-insensitively.
 */
enum class MediaSource(val patterns: List<String>, val partOf: MediaSource? = null) {
    CAMERA(listOf("DCIM/Camera/%")),
    SCREENSHOTS(listOf("%Screenshot%")),
    SCREEN_RECORDINGS(listOf("%Screen record%", "%Screenrecord%", "%ScreenRecorder%")),
    WHATSAPP(listOf("%WhatsApp%")),
    SNAPCHAT(listOf("%Snapchat%")),
    TELEGRAM(listOf("%Telegram%")),
    INSTAGRAM(listOf("%Instagram%")),
    MESSENGER(listOf("%Messenger%", "%Facebook%")),
    TIKTOK(listOf("%TikTok%")),
    DOWNLOADS(listOf("Download/%")),

    // Parts of WhatsApp, shown on its own page rather than in the main list.
    WHATSAPP_PHOTOS(listOf("%WhatsApp%Images/%"), partOf = WHATSAPP),
    WHATSAPP_VIDEOS(listOf("%WhatsApp%Video/%"), partOf = WHATSAPP),
    WHATSAPP_SENT(listOf("%WhatsApp%/Sent/"), partOf = WHATSAPP),
    WHATSAPP_PRIVATE(listOf("%WhatsApp%/Private/"), partOf = WHATSAPP),
    ;

    private val regexes by lazy {
        patterns.map { pattern ->
            Regex(pattern.split('%').joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
        }
    }

    fun matches(relativePath: String?): Boolean =
        relativePath != null && regexes.any { it.matches(relativePath) }
}

/** A folder of photos and videos, as MediaStore groups them. */
data class Album(
    val relativePath: String,
    val name: String,
    val count: Int,
    val sizeBytes: Long,
    val toReview: Int,
    val newest: Long,
    val coverUri: String?,
) {
    /** Android only lets apps move photos into DCIM/ or Pictures/ folders. */
    val canReceivePhotos: Boolean get() = AlbumPaths.isPhotoFolder(relativePath)
}

data class DateBucket(
    val year: Int,
    val month: Int,
    val count: Int,
    val sizeBytes: Long,
    val toReview: Int,
)

data class SourceCount(val source: MediaSource, val count: Int, val toReview: Int)

data class DuplicateGroup(
    val photos: List<Photo>,
    val bestId: Long,
) {
    val reclaimableBytes: Long get() = photos.filter { it.id != bestId }.sumOf { it.sizeBytes }
}

data class LibraryStats(
    val kept: Int = 0,
    val movedToTrash: Int = 0,
    val deletedFromPhone: Int = 0,
    val freedBytes: Long = 0,
    val movedToVault: Int = 0,
    val skipped: Int = 0,
    val inVault: Int = 0,
) {
    val reviewed: Int get() = kept + movedToTrash + movedToVault
}

data class QuickCounts(
    val videosToReview: Int = 0,
    val videos: Int = 0,
    val blurryToReview: Int = 0,
    val largeToReview: Int = 0,
    val albums: Int = 0,
    val years: Int = 0,
)

data class AnalysisProgress(val analysed: Int, val total: Int) {
    val isComplete: Boolean get() = analysed >= total
}

/**
 * Result of moving photos between albums. [copies] are photos Android wouldn't move in place,
 * copied into the album instead; their originals still need removing (see
 * LibraryRepository.completeCopies).
 */
data class MoveResult(val moved: Int, val failed: Int, val copies: List<MovedCopy> = emptyList())

/** A photo copied into an album because it couldn't be moved; the original is still there. */
data class MovedCopy(val original: Photo, val copyId: Long, val copyUri: String)

/** Folder rules for creating, renaming and merging albums. */
object AlbumPaths {
    private val photoRoots = listOf("DCIM/", "Pictures/")

    fun isPhotoFolder(relativePath: String): Boolean = photoRoots.any { relativePath.startsWith(it, ignoreCase = true) }

    /** A user-typed album name made safe for a folder name, or null if nothing usable is left. */
    fun cleanName(name: String): String? =
        name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "").trim().trim('.').take(MAX_NAME).ifEmpty { null }

    /** New album folder for [name]. */
    fun newAlbum(name: String): String = "Pictures/$name/"

    /** The folder [relativePath] would have after a rename; outside DCIM/Pictures it moves into Pictures. */
    fun renamed(relativePath: String, name: String): String {
        val parent = relativePath.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isNotEmpty() && isPhotoFolder("$parent/")) "$parent/$name/" else newAlbum(name)
    }

    /**
     * The folder's own name: "DCIM/Camera/" → "Camera". A folder inside one of WhatsApp's media
     * folders keeps that name in front, since "Sent" alone could be anything:
     * ".../WhatsApp Images/Sent/" → "WhatsApp Images · Sent".
     */
    fun displayName(relativePath: String): String {
        val parts = relativePath.trimEnd('/').split('/')
        val leaf = parts.last().ifEmpty { return relativePath }
        val parent = parts.getOrNull(parts.size - 2)
        return if (parent != null && isWhatsAppMediaFolder(parent)) "$parent · $leaf" else leaf
    }

    /** "WhatsApp Images", "WhatsApp Video" and the WhatsApp Business equivalents. */
    fun isWhatsAppMediaFolder(name: String): Boolean =
        name.startsWith("WhatsApp") && (name.endsWith(" Images") || name.endsWith(" Video"))

    private const val MAX_NAME = 60
}

/** Start (inclusive) and end (exclusive) epoch millis of calendar periods, in the phone's time zone. */
object DateRanges {
    fun year(year: Int, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until end
    }

    fun month(year: Int, month: Int, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val ym = YearMonth.of(year, month)
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until end
    }
}

/** Photos in one smart category, and how many of them are still to be sorted. */
data class CategoryCount(val category: SmartCategory, val count: Int, val toReview: Int)
