package com.dosetrack.app.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val remindersEnabled: Boolean = true,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Profile shown in the UI. UI state only: reminder scheduling and notifications never read it,
     * so switching profiles can't affect any reminder.
     */
    val activeProfileId: Long = 1,
    /** Today shows every profile's doses ("Everyone") instead of only the active profile. */
    val todayShowsEveryone: Boolean = false,
) {
    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
    }
}

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        val TODAY_SHOWS_EVERYONE = booleanPreferencesKey("today_shows_everyone")
    }

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone,
                remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: defaults.remindersEnabled,
                snoozeMinutes = prefs[Keys.SNOOZE_MINUTES] ?: defaults.snoozeMinutes,
                soundEnabled = prefs[Keys.SOUND_ENABLED] ?: defaults.soundEnabled,
                vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: defaults.vibrationEnabled,
                themeMode = prefs[Keys.THEME_MODE]
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: defaults.themeMode,
                activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID] ?: defaults.activeProfileId,
                todayShowsEveryone = prefs[Keys.TODAY_SHOWS_EVERYONE] ?: defaults.todayShowsEveryone,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = dataStore.edit { it[Keys.ONBOARDING_DONE] = done }

    suspend fun setRemindersEnabled(enabled: Boolean) = dataStore.edit { it[Keys.REMINDERS_ENABLED] = enabled }

    suspend fun setSnoozeMinutes(minutes: Int) = dataStore.edit { it[Keys.SNOOZE_MINUTES] = minutes }

    suspend fun setSoundEnabled(enabled: Boolean) = dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }

    suspend fun setVibrationEnabled(enabled: Boolean) = dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[Keys.THEME_MODE] = mode.name }

    /** Selecting a profile also leaves Today's "Everyone" view. */
    suspend fun setActiveProfile(profileId: Long) = dataStore.edit {
        it[Keys.ACTIVE_PROFILE_ID] = profileId
        it[Keys.TODAY_SHOWS_EVERYONE] = false
    }

    suspend fun setTodayShowsEveryone(everyone: Boolean) = dataStore.edit { it[Keys.TODAY_SHOWS_EVERYONE] = everyone }
}
