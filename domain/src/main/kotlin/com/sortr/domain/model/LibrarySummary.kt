package com.sortr.domain.model

data class LibrarySummary(
    /** Photos and videos in the library, not counting the Trash and the vault. */
    val total: Int = 0,
    val videos: Int = 0,
    val unreviewed: Int = 0,
    val skipped: Int = 0,
    val kept: Int = 0,
    val trashedCount: Int = 0,
    val trashedBytes: Long = 0,
    /** Swiped into the vault and still in the gallery. */
    val vaultedCount: Int = 0,
) {
    /** Photos a new "All photos" session would show. */
    val toReview: Int get() = unreviewed + skipped

    val photos: Int get() = total - videos

    /** Share of everything Sortr knows about that has been decided on, from 0 to 1. */
    val sortedFraction: Float
        get() {
            val all = total + trashedCount + vaultedCount
            return if (all == 0) 0f else 1f - toReview.toFloat() / all
        }
}
