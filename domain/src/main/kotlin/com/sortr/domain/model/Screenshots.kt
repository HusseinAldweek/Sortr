package com.sortr.domain.model

/** How Sortr recognises screenshots and where tidying up puts them. */
object ScreenshotRules {
    /** SQL LIKE patterns for a screenshot's folder. */
    val FOLDER_PATTERNS = listOf("%Screenshot%")

    /** SQL LIKE patterns for a screenshot's file name; `_` also matches "Screen shot" and "Screen-shot". */
    val NAME_PATTERNS = listOf("Screenshot%", "Screen_shot%")

    /** Where screenshots go when no screenshot album exists yet. */
    const val DEFAULT_ALBUM = "Pictures/Screenshots/"

    /** "Older screenshots" means older than this many days. */
    const val OLD_AFTER_DAYS = 30

    /**
     * The album every screenshot belongs in: the biggest existing screenshot album that Android
     * lets apps move items into, otherwise [DEFAULT_ALBUM].
     */
    fun homeAlbum(folders: List<ScreenshotFolder>): String =
        folders
            .filter { it.relativePath.contains("screenshot", ignoreCase = true) && AlbumPaths.isPhotoFolder(it.relativePath) }
            .maxByOrNull { it.count }
            ?.relativePath
            ?: DEFAULT_ALBUM
}

/** Screenshots in one folder. */
data class ScreenshotFolder(val relativePath: String, val count: Int)

/** Every screenshot outside the Trash and the vault, per folder. */
data class ScreenshotSummary(
    val total: Int = 0,
    val sizeBytes: Long = 0,
    val toReview: Int = 0,
    /** To sort and older than [ScreenshotRules.OLD_AFTER_DAYS] days. */
    val oldToReview: Int = 0,
    val folders: List<ScreenshotFolder> = emptyList(),
) {
    /** Where tidying up puts every screenshot. */
    val homeAlbum: String get() = ScreenshotRules.homeAlbum(folders)

    /** Screenshots that aren't in [homeAlbum] yet. */
    val outsideHome: Int get() = folders.filter { it.relativePath != homeAlbum }.sumOf { it.count }

    /** Folders other than [homeAlbum] that hold screenshots. */
    val otherFolders: Int get() = folders.count { it.relativePath != homeAlbum }
}
