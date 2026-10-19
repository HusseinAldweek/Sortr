package com.sortr.app.ui.swipe

import com.sortr.domain.model.SwipeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwipeGestureTest {

    private val width = 1000f
    private val height = 1600f
    private val fling = 3000f
    private val allActions = SwipeAction.entries.toSet()
    private val phaseOneActions = setOf(SwipeAction.KEEP, SwipeAction.DELETE, SwipeAction.SKIP)

    private fun resolve(
        dx: Float,
        dy: Float,
        vx: Float = 0f,
        vy: Float = 0f,
        enabled: Set<SwipeAction> = allActions,
    ) = resolveSwipe(dx, dy, vx, vy, width, height, fling, enabled)

    @Test
    fun `dragging past the threshold commits in each direction`() {
        assertEquals(SwipeAction.KEEP, resolve(dx = 300f, dy = 20f))
        assertEquals(SwipeAction.DELETE, resolve(dx = -300f, dy = 20f))
        assertEquals(SwipeAction.PRIVATE, resolve(dx = 10f, dy = -300f))
        assertEquals(SwipeAction.SKIP, resolve(dx = 10f, dy = 300f))
    }

    @Test
    fun `a short slow drag springs back`() {
        assertNull(resolve(dx = 200f, dy = 0f))
        assertNull(resolve(dx = 0f, dy = 250f))
        assertNull(resolve(dx = 0f, dy = 0f))
    }

    @Test
    fun `a fast flick commits before the distance threshold`() {
        assertEquals(SwipeAction.KEEP, resolve(dx = 100f, dy = 0f, vx = 4000f))
        assertEquals(SwipeAction.DELETE, resolve(dx = -100f, dy = 0f, vx = -4000f))
    }

    @Test
    fun `a flick needs some movement and must go the same way as the drag`() {
        // Barely moved.
        assertNull(resolve(dx = 20f, dy = 0f, vx = 5000f))
        // Dragged right but flung back left.
        assertNull(resolve(dx = 100f, dy = 0f, vx = -5000f))
    }

    @Test
    fun `the dominant axis decides the direction`() {
        assertEquals(SwipeAction.KEEP, resolve(dx = 400f, dy = 350f))
        assertEquals(SwipeAction.SKIP, resolve(dx = 300f, dy = 450f))
    }

    @Test
    fun `disabled directions spring back`() {
        assertNull(resolve(dx = 0f, dy = -600f, enabled = phaseOneActions))
        assertEquals(SwipeAction.SKIP, resolve(dx = 0f, dy = 600f, enabled = phaseOneActions))
    }

    @Test
    fun `progress grows to one at the commit distance`() {
        assertEquals(0f, dragProgress(0f, 0f, width, height))
        assertEquals(0.5f, dragProgress(width * HORIZONTAL_COMMIT_FRACTION / 2, 0f, width, height), 0.001f)
        assertEquals(1f, dragProgress(width, 0f, width, height))
        assertEquals(1f, dragProgress(0f, -height * VERTICAL_COMMIT_FRACTION, width, height), 0.001f)
    }

    @Test
    fun `nothing resolves before the card has been measured`() {
        assertNull(resolveSwipe(500f, 0f, 0f, 0f, 0f, 0f, fling, allActions))
        assertEquals(0f, dragProgress(500f, 0f, 0f, 0f))
    }
}
