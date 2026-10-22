package com.sortr.domain.analysis

/** Turns what the user typed into a safe full-text query. */
object TextSearchQuery {

    /**
     * Every word must appear, each as a prefix ("addr" finds "address"). Punctuation and FTS
     * operators the user types are dropped, so the query can't be malformed. Null when there's
     * nothing to search for.
     */
    fun build(input: String): String? {
        val words = WORD.findAll(input.lowercase()).map { it.value }.take(MAX_WORDS).toList()
        if (words.isEmpty()) return null
        return words.joinToString(" ") { "\"$it*\"" }
    }

    private val WORD = Regex("""[\p{L}\p{N}]+""")
    private const val MAX_WORDS = 8
}
