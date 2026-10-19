package com.sortr.app.ui.swipe

import com.sortr.domain.model.SwipeAction
import kotlin.math.abs

/** How far a card must be dragged, as a fraction of its size, before letting go commits. */
internal const val HORIZONTAL_COMMIT_FRACTION = 0.28f
internal const val VERTICAL_COMMIT_FRACTION = 0.18f

/** A quick flick commits early, but only once the card has moved at least this far. */
internal const val FLING_MIN_FRACTION = 0.05f

/** The direction a card is being dragged in, by whichever axis dominates. Null at rest. */
internal fun dragDirection(dx: Float, dy: Float): SwipeAction? = when {
    dx == 0f && dy == 0f -> null
    abs(dx) >= abs(dy) -> if (dx > 0f) SwipeAction.KEEP else SwipeAction.DELETE
    else -> if (dy < 0f) SwipeAction.PRIVATE else SwipeAction.SKIP
}

/** 0 at rest, 1 once the drag is far enough to commit on release. Drives the overlay. */
internal fun dragProgress(dx: Float, dy: Float, width: Float, height: Float): Float {
    if (width <= 0f || height <= 0f) return 0f
    val progress = when (dragDirection(dx, dy)) {
        SwipeAction.KEEP, SwipeAction.DELETE -> abs(dx) / (width * HORIZONTAL_COMMIT_FRACTION)
        SwipeAction.PRIVATE, SwipeAction.SKIP -> abs(dy) / (height * VERTICAL_COMMIT_FRACTION)
        null -> 0f
    }
    return progress.coerceIn(0f, 1f)
}

/**
 * The action a released drag commits to, or null if the card should spring back.
 * Velocities are in pixels per second, positive right and down.
 */
internal fun resolveSwipe(
    dx: Float,
    dy: Float,
    velocityX: Float,
    velocityY: Float,
    width: Float,
    height: Float,
    flingVelocity: Float,
    enabled: Set<SwipeAction>,
): SwipeAction? {
    if (width <= 0f || height <= 0f) return null
    val direction = dragDirection(dx, dy) ?: return null
    if (direction !in enabled) return null

    // Distance and speed along the chosen direction, positive when moving that way.
    val distance: Float
    val speed: Float
    val commitDistance: Float
    val size: Float
    when (direction) {
        SwipeAction.KEEP -> { distance = dx; speed = velocityX; size = width; commitDistance = width * HORIZONTAL_COMMIT_FRACTION }
        SwipeAction.DELETE -> { distance = -dx; speed = -velocityX; size = width; commitDistance = width * HORIZONTAL_COMMIT_FRACTION }
        SwipeAction.PRIVATE -> { distance = -dy; speed = -velocityY; size = height; commitDistance = height * VERTICAL_COMMIT_FRACTION }
        SwipeAction.SKIP -> { distance = dy; speed = velocityY; size = height; commitDistance = height * VERTICAL_COMMIT_FRACTION }
    }
    val farEnough = distance >= commitDistance
    val flung = speed >= flingVelocity && distance >= size * FLING_MIN_FRACTION
    return direction.takeIf { farEnough || flung }
}
