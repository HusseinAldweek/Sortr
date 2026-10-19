package com.sortr.domain.repository

import kotlinx.coroutines.flow.Flow

interface AppSettings {

    val onboardingDone: Flow<Boolean>

    suspend fun setOnboardingDone()

    /** Cosine similarity needed for two faces to count as the same person. */
    val faceSimilarityThreshold: Flow<Float>

    suspend fun setFaceSimilarityThreshold(value: Float)

    val scanOnlyWhileCharging: Flow<Boolean>

    suspend fun setScanOnlyWhileCharging(value: Boolean)

    /** Whether Sortr has already asked to show the scanning notification. */
    val notificationPermissionAsked: Flow<Boolean>

    suspend fun setNotificationPermissionAsked()
}
