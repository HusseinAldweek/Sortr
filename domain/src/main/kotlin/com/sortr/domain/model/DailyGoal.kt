package com.sortr.domain.model

import java.time.LocalDate

/** Progress towards the daily sorting goal. [goal] is 0 when no goal is set. */
data class GoalProgress(val goal: Int = 0, val today: Int = 0, val streak: Int = 0) {
    val enabled: Boolean get() = goal > 0
    val reached: Boolean get() = enabled && today >= goal

    /** From 0 to 1. */
    val fraction: Float get() = if (!enabled) 0f else (today.toFloat() / goal).coerceAtMost(1f)
}

object DailyGoal {
    /** Goals offered in Settings. */
    val CHOICES = listOf(25, 50, 100, 200)

    /** Default reminder time, minutes after midnight (7 pm). */
    const val DEFAULT_REMINDER_MINUTES = 19 * 60

    /** How far back streaks are counted. */
    const val STREAK_WINDOW_DAYS = 400

    /**
     * Days in a row on which at least [goal] photos were sorted. Today counts once the goal is
     * reached; until then the streak carries on from yesterday, so it isn't lost in the morning.
     */
    fun streak(sortedPerDay: Map<LocalDate, Int>, goal: Int, today: LocalDate): Int {
        if (goal <= 0) return 0
        var day = if ((sortedPerDay[today] ?: 0) >= goal) today else today.minusDays(1)
        var streak = 0
        while ((sortedPerDay[day] ?: 0) >= goal) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun progress(sortedPerDay: Map<LocalDate, Int>, goal: Int, today: LocalDate): GoalProgress =
        GoalProgress(goal = goal.coerceAtLeast(0), today = sortedPerDay[today] ?: 0, streak = streak(sortedPerDay, goal, today))
}
