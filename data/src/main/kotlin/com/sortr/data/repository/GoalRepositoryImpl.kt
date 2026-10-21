package com.sortr.data.repository

import com.sortr.data.db.SortrDatabase
import com.sortr.domain.model.DailyGoal
import com.sortr.domain.model.GoalProgress
import com.sortr.domain.repository.AppSettings
import com.sortr.domain.repository.GoalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepositoryImpl @Inject constructor(
    db: SortrDatabase,
    settings: AppSettings,
) : GoalRepository {

    private val actionDao = db.actionDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val progress: Flow<GoalProgress> = settings.dailyGoal.flatMapLatest { goal ->
        if (goal <= 0) {
            flowOf(GoalProgress())
        } else {
            val today = LocalDate.now()
            val since = today.minusDays(DailyGoal.STREAK_WINDOW_DAYS.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            actionDao.observeSortedPerDay(since).map { rows ->
                val perDay = rows.mapNotNull { row -> runCatching { LocalDate.parse(row.day) to row.count }.getOrNull() }.toMap()
                // Read the date again: the flow may outlive midnight.
                DailyGoal.progress(perDay, goal, LocalDate.now())
            }
        }
    }.distinctUntilChanged()
}
