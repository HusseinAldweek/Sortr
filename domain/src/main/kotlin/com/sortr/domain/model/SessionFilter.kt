package com.sortr.domain.model

import com.sortr.domain.analysis.SmartCategory
import java.net.URLDecoder
import java.net.URLEncoder

/** Which photos a swipe session draws from. */
sealed interface SessionFilter {
    /** Stable string form, used as a navigation argument. */
    val key: String

    data object AllPhotos : SessionFilter {
        override val key = "all"
    }

    /** Photos with this person's face in them. */
    data class Person(val personId: Long) : SessionFilter {
        override val key = "person:$personId"
    }

    /** Photos with a face that wasn't confidently matched to anyone. */
    data object UnsortedFaces : SessionFilter {
        override val key = "unsorted-faces"
    }

    data class Year(val year: Int) : SessionFilter {
        override val key = "year:$year"
    }

    data class Month(val year: Int, val month: Int) : SessionFilter {
        override val key = "month:$year-$month"
    }

    data class Album(val relativePath: String) : SessionFilter {
        override val key = "album:${encode(relativePath)}"
    }

    data class Source(val source: MediaSource) : SessionFilter {
        override val key = "source:${source.name}"
    }

    data object Videos : SessionFilter {
        override val key = "videos"
    }

    /** Photos with a low sharpness score, blurriest first. */
    data object Blurry : SessionFilter {
        override val key = "blurry"
    }

    /** In each group of similar photos, every copy except the best one. */
    data object Duplicates : SessionFilter {
        override val key = "duplicates"
    }

    /** In each group of byte-identical photos and videos, every copy except the one to keep. */
    data object ExactDuplicates : SessionFilter {
        override val key = "exact-duplicates"
    }

    /** Files of at least [LARGE_FILE_BYTES], largest first. */
    data object Largest : SessionFilter {
        override val key = "largest"
    }

    /**
     * Screenshots, found by folder or by file name, so ones saved in other folders count too.
     * With [olderThanDays], only screenshots older than that.
     */
    data class Screenshots(val olderThanDays: Int? = null) : SessionFilter {
        override val key = if (olderThanDays == null) "screenshots" else "screenshots:$olderThanDays"
    }

    /** Photos and videos taken on today's date in earlier years. */
    data object OnThisDay : SessionFilter {
        override val key = "on-this-day"
    }

    /** Photos Sortr recognised as [category], e.g. receipts or pets. */
    data class Category(val category: SmartCategory) : SessionFilter {
        override val key = "category:${category.name}"
    }

    /** Photos with these words in them. */
    data class TextSearch(val query: String) : SessionFilter {
        override val key = "search:${encode(query)}"
    }

    /** A mix chosen by the user. Empty [albums] means any album. */
    data class Custom(
        val mediaType: MediaTypeFilter = MediaTypeFilter.ALL,
        val year: Int? = null,
        val month: Int? = null,
        val albums: Set<String> = emptySet(),
        val minSizeBytes: Long? = null,
    ) : SessionFilter {
        override val key: String
            get() = buildList {
                add("t=${mediaType.name}")
                year?.let { add("y=$it") }
                month?.let { add("m=$it") }
                if (albums.isNotEmpty()) add("a=" + albums.sorted().joinToString(",") { encode(it) })
                minSizeBytes?.let { add("s=$it") }
            }.joinToString("&", prefix = CUSTOM_PREFIX)
    }

    companion object {
        const val LARGE_FILE_BYTES = 5L * 1024 * 1024
        private const val CUSTOM_PREFIX = "custom:"

        fun fromKey(key: String): SessionFilter = runCatching { parse(key) }.getOrNull() ?: AllPhotos

        private fun parse(key: String): SessionFilter? {
            val type = key.substringBefore(':')
            val value = key.substringAfter(':', missingDelimiterValue = "")
            return when (type) {
                "all" -> AllPhotos
                "unsorted-faces" -> UnsortedFaces
                "videos" -> Videos
                "blurry" -> Blurry
                "duplicates" -> Duplicates
                "exact-duplicates" -> ExactDuplicates
                "largest" -> Largest
                "on-this-day" -> OnThisDay
                "screenshots" -> Screenshots(value.toIntOrNull()?.takeIf { it > 0 })
                "category" -> Category(SmartCategory.valueOf(value))
                "search" -> TextSearch(decode(value))
                "person" -> value.toLongOrNull()?.let(::Person)
                "year" -> value.toIntOrNull()?.let(::Year)
                "month" -> {
                    val (year, month) = value.split('-').map { it.toInt() }
                    Month(year, month).takeIf { month in 1..12 }
                }
                "album" -> Album(decode(value))
                "source" -> Source(MediaSource.valueOf(value))
                "custom" -> parseCustom(value)
                else -> null
            }
        }

        private fun parseCustom(value: String): Custom {
            val parts = value.split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return Custom(
                mediaType = parts["t"]?.let { MediaTypeFilter.valueOf(it) } ?: MediaTypeFilter.ALL,
                year = parts["y"]?.toInt(),
                month = parts["m"]?.toInt()?.takeIf { it in 1..12 },
                albums = parts["a"]?.split(',')?.filter { it.isNotEmpty() }?.map(::decode)?.toSet() ?: emptySet(),
                minSizeBytes = parts["s"]?.toLong(),
            )
        }

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

        private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
