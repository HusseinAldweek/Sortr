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

    /** How the album list is ordered; null until the user picks one. */
    val albumSort: Flow<String?>

    suspend fun setAlbumSort(value: String)

    /** Folders outside the gallery the user let Sortr read (document tree URIs). */
    val hiddenFolders: Flow<Set<String>>

    suspend fun addHiddenFolder(treeUri: String)

    suspend fun removeHiddenFolder(treeUri: String)

    /** The user closed the Home card offering hidden WhatsApp media. */
    val whatsAppPromptDismissed: Flow<Boolean>

    suspend fun dismissWhatsAppPrompt()

    /** Photos to sort each day; 0 means no goal. */
    val dailyGoal: Flow<Int>

    suspend fun setDailyGoal(value: Int)

    /** Minutes after midnight for the daily goal reminder; null when it's off. */
    val goalReminderMinutes: Flow<Int?>

    suspend fun setGoalReminderMinutes(value: Int?)
}
