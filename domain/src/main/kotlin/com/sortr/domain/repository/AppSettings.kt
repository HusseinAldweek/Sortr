package com.sortr.domain.repository

import kotlinx.coroutines.flow.Flow

interface AppSettings {

    val onboardingDone: Flow<Boolean>

    suspend fun setOnboardingDone()
}
