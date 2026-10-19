package com.sortr.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.sortr.domain.repository.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
