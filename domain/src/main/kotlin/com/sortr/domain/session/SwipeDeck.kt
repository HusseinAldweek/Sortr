package com.sortr.domain.session

import com.sortr.domain.model.Decision
import com.sortr.domain.model.Photo

/**
 * In-memory state of one swipe session: a fixed list of photos, how far the user has got,
 * and the decisions that can still be undone.
 *
 * Decisions are strictly sequential, so the most recent undoable decision always belongs to
 * the photo just before [position].
 */
class SwipeDeck(
    photos: List<Photo>,
    private val undoCapacity: Int = DEFAULT_UNDO_CAPACITY,
) {
    private val photos = photos.toList()
    private val history = ArrayDeque<Decision>()

    init {
        require(undoCapacity > 0) { "undoCapacity must be positive" }
    }

    var position: Int = 0
        private set

    val total: Int get() = photos.size

    val current: Photo? get() = photos.getOrNull(position)

    val isFinished: Boolean get() = position >= photos.size

    val canUndo: Boolean get() = history.isNotEmpty()

    val lastDecision: Decision? get() = history.lastOrNull()

    /** The photos right after the current one, for preloading. */
    fun upcoming(count: Int): List<Photo> {
        val from = (position + 1).coerceAtMost(photos.size)
        val to = (from + count).coerceAtMost(photos.size)
        return photos.subList(from, to)
    }

    /** Records a decision on the current photo and advances to the next one. */
    fun commit(decision: Decision) {
        val photo = checkNotNull(current) { "No photo left to decide on" }
        require(decision.photo.id == photo.id) {
            "Decision is for photo ${decision.photo.id} but the current photo is ${photo.id}"
        }
        history.addLast(decision)
        if (history.size > undoCapacity) history.removeFirst()
        position++
    }

    /** Steps back over the last decision. Returns it, or null when there is nothing to undo. */
    fun undoLast(): Decision? {
        val decision = history.removeLastOrNull() ?: return null
        position--
        return decision
    }

    /** Makes the decisions so far final, e.g. once vaulted originals have left the gallery. */
    fun clearHistory() {
        history.clear()
    }

    companion object {
        const val DEFAULT_UNDO_CAPACITY = 50
    }
}
