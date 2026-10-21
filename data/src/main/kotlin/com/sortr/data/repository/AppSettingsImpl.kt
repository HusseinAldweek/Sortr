package com.sortr.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sortr.domain.faces.FaceClusterer
import com.sortr.domain.repository.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore(name = "settings")

@Singleton
class AppSettingsImpl @Inject constructor(
    @ApplicationContext context: Context,
) : AppSettings {

    private val store = context.settingsStore

    private val preferences = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val onboardingDone: Flow<Boolean> = preferences.map { it[ONBOARDING_DONE] ?: false }

    override suspend fun setOnboardingDone() {
        store.edit { it[ONBOARDING_DONE] = true }
    }

    override val faceSimilarityThreshold: Flow<Float> = preferences
        .map { (it[FACE_THRESHOLD] ?: FaceClusterer.DEFAULT_THRESHOLD).coerceIn(FaceClusterer.MIN_THRESHOLD, FaceClusterer.MAX_THRESHOLD) }
        .distinctUntilChanged()

    override suspend fun setFaceSimilarityThreshold(value: Float) {
        store.edit { it[FACE_THRESHOLD] = value.coerceIn(FaceClusterer.MIN_THRESHOLD, FaceClusterer.MAX_THRESHOLD) }
    }

    override val scanOnlyWhileCharging: Flow<Boolean> =
        preferences.map { it[SCAN_ONLY_WHILE_CHARGING] ?: false }.distinctUntilChanged()

    override suspend fun setScanOnlyWhileCharging(value: Boolean) {
        store.edit { it[SCAN_ONLY_WHILE_CHARGING] = value }
    }

    override val notificationPermissionAsked: Flow<Boolean> =
        preferences.map { it[NOTIFICATION_PERMISSION_ASKED] ?: false }

    override suspend fun setNotificationPermissionAsked() {
        store.edit { it[NOTIFICATION_PERMISSION_ASKED] = true }
    }

    override val albumSort: Flow<String?> = preferences.map { it[ALBUM_SORT] }

    override suspend fun setAlbumSort(value: String) {
        store.edit { it[ALBUM_SORT] = value }
    }

    override val hiddenFolders: Flow<Set<String>> = preferences.map { it[HIDDEN_FOLDERS] ?: emptySet() }

    override suspend fun addHiddenFolder(treeUri: String) {
        store.edit { it[HIDDEN_FOLDERS] = (it[HIDDEN_FOLDERS] ?: emptySet()) + treeUri }
    }

    override suspend fun removeHiddenFolder(treeUri: String) {
        store.edit { it[HIDDEN_FOLDERS] = (it[HIDDEN_FOLDERS] ?: emptySet()) - treeUri }
    }

    override val whatsAppPromptDismissed: Flow<Boolean> = preferences.map { it[WHATSAPP_PROMPT_DISMISSED] ?: false }

    override suspend fun dismissWhatsAppPrompt() {
        store.edit { it[WHATSAPP_PROMPT_DISMISSED] = true }
    }

    override val dailyGoal: Flow<Int> = preferences.map { (it[DAILY_GOAL] ?: 0).coerceAtLeast(0) }.distinctUntilChanged()

    override suspend fun setDailyGoal(value: Int) {
        store.edit { it[DAILY_GOAL] = value.coerceAtLeast(0) }
    }

    override val goalReminderMinutes: Flow<Int?> =
        preferences.map { prefs -> prefs[GOAL_REMINDER_MINUTES]?.takeIf { it in 0 until 24 * 60 } }.distinctUntilChanged()

    override suspend fun setGoalReminderMinutes(value: Int?) {
        store.edit {
            if (value == null) it.remove(GOAL_REMINDER_MINUTES) else it[GOAL_REMINDER_MINUTES] = value.coerceIn(0, 24 * 60 - 1)
        }
    }

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val FACE_THRESHOLD = floatPreferencesKey("face_similarity_threshold")
        val SCAN_ONLY_WHILE_CHARGING = booleanPreferencesKey("scan_only_while_charging")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
        val ALBUM_SORT = stringPreferencesKey("album_sort")
        val HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folders")
        val WHATSAPP_PROMPT_DISMISSED = booleanPreferencesKey("whatsapp_prompt_dismissed")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val GOAL_REMINDER_MINUTES = intPreferencesKey("goal_reminder_minutes")
    }
}
