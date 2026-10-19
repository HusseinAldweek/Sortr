package com.sortr.domain.model

data class LibrarySummary(
    val total: Int = 0,
    val unreviewed: Int = 0,
    val skipped: Int = 0,
    val kept: Int = 0,
    val trashedCount: Int = 0,
    val trashedBytes: Long = 0,
) {
    /** Photos a new "All photos" session would show. */
    val toReview: Int get() = unreviewed + skipped
}
