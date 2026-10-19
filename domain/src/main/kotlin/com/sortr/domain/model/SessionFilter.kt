package com.sortr.domain.model

/** Which photos a swipe session draws from. More filters arrive with later phases. */
sealed interface SessionFilter {
    /** Stable string form, used as a navigation argument. */
    val key: String

    data object AllPhotos : SessionFilter {
        override val key = "all"
    }

    companion object {
        fun fromKey(key: String): SessionFilter = when (key) {
            AllPhotos.key -> AllPhotos
            else -> AllPhotos
        }
    }
}
