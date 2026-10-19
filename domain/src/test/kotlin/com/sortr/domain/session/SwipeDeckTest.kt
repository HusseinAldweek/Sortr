package com.sortr.domain.session

import com.sortr.domain.model.Decision
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.model.SwipeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwipeDeckTest {

    private fun photo(id: Long) = Photo(
        id = id,
        uri = "content://media/external/images/media/$id",
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        dateTaken = id,
        sizeBytes = 1_000,
        width = 100,
        height = 100,
        bucketName = "Camera",
        relativePath = "DCIM/Camera/",
        status = PhotoStatus.UNREVIEWED,
    )

    private fun decide(deck: SwipeDeck, action: SwipeAction, actionId: Long = deck.position.toLong()) {
        val photo = deck.current!!
        deck.commit(Decision(actionId, photo, action, photo.status))
    }

    @Test
    fun `commit advances to the next photo`() {
        val deck = SwipeDeck(listOf(photo(1), photo(2), photo(3)))

        decide(deck, SwipeAction.KEEP)

        assertEquals(1, deck.position)
        assertEquals(2L, deck.current?.id)
        assertTrue(deck.canUndo)
    }

    @Test
    fun `undo returns to the previous photo and gives back its decision`() {
        val deck = SwipeDeck(listOf(photo(1), photo(2), photo(3)))
        decide(deck, SwipeAction.KEEP)
        decide(deck, SwipeAction.DELETE)

        val undone = deck.undoLast()

        assertEquals(SwipeAction.DELETE, undone?.action)
        assertEquals(2L, undone?.photo?.id)
        assertEquals(2L, deck.current?.id)
        assertEquals(1, deck.position)
    }

    @Test
    fun `undo on a fresh deck does nothing`() {
        val deck = SwipeDeck(listOf(photo(1)))

        assertNull(deck.undoLast())
        assertEquals(0, deck.position)
        assertFalse(deck.canUndo)
    }

    @Test
    fun `history keeps only the most recent decisions up to capacity`() {
        val deck = SwipeDeck((1L..30L).map(::photo), undoCapacity = 20)
        repeat(30) { decide(deck, SwipeAction.SKIP) }

        var undone = 0
        while (deck.undoLast() != null) undone++

        assertEquals(20, undone)
        assertEquals(10, deck.position)
        assertEquals(11L, deck.current?.id)
    }

    @Test
    fun `deck reports finished after the last photo and can undo back into it`() {
        val deck = SwipeDeck(listOf(photo(1), photo(2)))
        decide(deck, SwipeAction.KEEP)
        decide(deck, SwipeAction.KEEP)

        assertTrue(deck.isFinished)
        assertNull(deck.current)

        deck.undoLast()

        assertFalse(deck.isFinished)
        assertEquals(2L, deck.current?.id)
    }

    @Test
    fun `upcoming lists the photos after the current one`() {
        val deck = SwipeDeck((1L..5L).map(::photo))
        decide(deck, SwipeAction.KEEP)

        assertEquals(listOf(3L, 4L, 5L), deck.upcoming(3).map { it.id })
        assertEquals(listOf(3L, 4L, 5L), deck.upcoming(10).map { it.id })
    }

    @Test
    fun `upcoming is empty at the end of the deck`() {
        val deck = SwipeDeck(listOf(photo(1)))

        assertTrue(deck.upcoming(3).isEmpty())
        decide(deck, SwipeAction.KEEP)
        assertTrue(deck.upcoming(3).isEmpty())
    }

    @Test
    fun `committing a decision for a different photo is rejected`() {
        val deck = SwipeDeck(listOf(photo(1), photo(2)))

        assertFailsWith<IllegalArgumentException> {
            deck.commit(Decision(0, photo(2), SwipeAction.KEEP, PhotoStatus.UNREVIEWED))
        }
        assertEquals(0, deck.position)
    }

    @Test
    fun `clearing history keeps the position but disables undo`() {
        val deck = SwipeDeck(listOf(photo(1), photo(2), photo(3)))
        decide(deck, SwipeAction.PRIVATE)
        decide(deck, SwipeAction.KEEP)

        deck.clearHistory()

        assertFalse(deck.canUndo)
        assertNull(deck.undoLast())
        assertEquals(2, deck.position)
        assertEquals(3L, deck.current?.id)
    }

    @Test
    fun `committing past the end is rejected`() {
        val deck = SwipeDeck(listOf(photo(1)))
        decide(deck, SwipeAction.KEEP)

        assertFailsWith<IllegalStateException> {
            deck.commit(Decision(1, photo(1), SwipeAction.KEEP, PhotoStatus.UNREVIEWED))
        }
    }
}
