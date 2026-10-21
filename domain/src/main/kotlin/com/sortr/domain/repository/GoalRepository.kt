package com.sortr.domain.repository

import com.sortr.domain.model.GoalProgress
import kotlinx.coroutines.flow.Flow

/** The daily sorting goal: how many were sorted today, and the streak. */
interface GoalRepository {
    val progress: Flow<GoalProgress>
}
