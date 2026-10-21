package com.sortr.domain.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyGoalTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun days(vararg counts: Int): Map<LocalDate, Int> =
        counts.withIndex().associate { (back, count) -> today.minusDays(back.toLong()) to count }

    @Test
    fun `streak counts today once the goal is reached`() {
        assertEquals(3, DailyGoal.streak(days(50, 60, 50, 10), goal = 50, today = today))
    }

    @Test
    fun `streak carries on from yesterday until today's goal is reached`() {
        assertEquals(2, DailyGoal.streak(days(12, 50, 80, 0), goal = 50, today = today))
    }

    @Test
    fun `a missed day ends the streak`() {
        assertEquals(1, DailyGoal.streak(days(50, 49, 50, 50), goal = 50, today = today))
        assertEquals(0, DailyGoal.streak(days(0, 0, 50), goal = 50, today = today))
    }

    @Test
    fun `no goal means no streak`() {
        assertEquals(0, DailyGoal.streak(days(500, 500), goal = 0, today = today))
        val progress = DailyGoal.progress(days(500), goal = 0, today = today)
        assertFalse(progress.enabled)
        assertFalse(progress.reached)
        assertEquals(0f, progress.fraction)
    }

    @Test
    fun `progress reports today's count and caps the fraction`() {
        val progress = DailyGoal.progress(days(75, 50), goal = 50, today = today)
        assertEquals(75, progress.today)
        assertEquals(2, progress.streak)
        assertTrue(progress.reached)
        assertEquals(1f, progress.fraction)
    }
}
