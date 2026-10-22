package com.sortr.domain.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextSearchQueryTest {

    @Test
    fun `words become prefix terms that must all match`() {
        assertEquals("\"main*\" \"street*\"", TextSearchQuery.build("Main Street"))
    }

    @Test
    fun `operators and quotes can't break the query`() {
        assertEquals("\"a*\" \"or*\" \"b*\"", TextSearchQuery.build("a OR \"b"))
        assertEquals("\"wifi*\" \"code*\"", TextSearchQuery.build("  wifi-code!! "))
    }

    @Test
    fun `nothing to search for gives null`() {
        assertNull(TextSearchQuery.build(" -*\" "))
    }

    @Test
    fun `other alphabets and digits are kept`() {
        assertEquals("\"café*\" \"42*\"", TextSearchQuery.build("Café 42"))
    }
}
