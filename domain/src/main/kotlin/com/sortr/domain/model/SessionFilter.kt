package com.sortr.domain.model

/** Which photos a swipe session draws from. More filters arrive with later phases. */
sealed interface SessionFilter {
    /** Stable string form, used as a navigation argument. */
    val key: String

    data object AllPhotos : SessionFilter {
        override val key = "all"
    }

    /** Photos with this person's face in them. */
    data class Person(val personId: Long) : SessionFilter {
        override val key = "$PERSON_PREFIX$personId"
    }

    /** Photos with a face that wasn't confidently matched to anyone. */
    data object UnsortedFaces : SessionFilter {
        override val key = "unsorted-faces"
    }

    companion object {
        private const val PERSON_PREFIX = "person:"

        fun fromKey(key: String): SessionFilter = when {
            key == UnsortedFaces.key -> UnsortedFaces
            key.startsWith(PERSON_PREFIX) -> key.removePrefix(PERSON_PREFIX).toLongOrNull()?.let(::Person) ?: AllPhotos
            else -> AllPhotos
        }
    }
}
