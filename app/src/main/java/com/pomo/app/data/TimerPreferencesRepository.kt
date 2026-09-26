package com.pomo.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pomo.app.model.AppSettings
import com.pomo.app.model.TimerMode
import com.pomo.app.model.TimerPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "pomo_preferences")

class TimerPreferencesRepository(private val context: Context) {
    companion object {
        private val KEY_PRESET = stringPreferencesKey("timer_preset")
        private val KEY_MODE = stringPreferencesKey("timer_mode")
        private val KEY_CUSTOM_WORK = intPreferencesKey("custom_work")
        private val KEY_CUSTOM_BREAK = intPreferencesKey("custom_break")
        private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val KEY_SOUND_VOLUME = floatPreferencesKey("sound_volume")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_AUTO_START = booleanPreferencesKey("auto_start")
        private val KEY_STREAK_DATE = stringPreferencesKey("streak_date")
        private val KEY_STREAK_COUNT = intPreferencesKey("streak_count")

        // Running-timer state, mirroring the web app's `pomo_timer_state`
        // localStorage entry: { modeKey, isActive, timeLeft, endTime }.
        private val KEY_TIMER_MODE_KEY = stringPreferencesKey("timer_state_mode")
        private val KEY_TIMER_ACTIVE = booleanPreferencesKey("timer_state_active")
        private val KEY_TIMER_TIME_LEFT = intPreferencesKey("timer_state_time_left")
        private val KEY_TIMER_END_TIME = longPreferencesKey("timer_state_end_time")
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    val preferencesFlow: Flow<StoredPreferences> = context.dataStore.data.map { prefs ->
        val presetStr = prefs[KEY_PRESET] ?: TimerPreset.P_25_5.name
        val modeStr = prefs[KEY_MODE] ?: TimerMode.WORK.name
        val customWork = prefs[KEY_CUSTOM_WORK] ?: 25
        val customBreak = prefs[KEY_CUSTOM_BREAK] ?: 5

        val soundEnabled = prefs[KEY_SOUND_ENABLED] ?: true
        val soundVolume = prefs[KEY_SOUND_VOLUME] ?: 0.8f
        val hapticsEnabled = prefs[KEY_HAPTICS_ENABLED] ?: true
        val notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: false
        val autoStart = prefs[KEY_AUTO_START] ?: true

        val today = getTodayDateString()
        val lastDate = prefs[KEY_STREAK_DATE] ?: today
        val streakCount = if (lastDate == today) prefs[KEY_STREAK_COUNT] ?: 0 else 0

        StoredPreferences(
            preset = try { TimerPreset.valueOf(presetStr) } catch (e: Exception) { TimerPreset.P_25_5 },
            mode = try { TimerMode.valueOf(modeStr) } catch (e: Exception) { TimerMode.WORK },
            customWork = customWork,
            customBreak = customBreak,
            settings = AppSettings(
                soundEnabled = soundEnabled,
                soundVolume = soundVolume,
                hapticsEnabled = hapticsEnabled,
                notificationsEnabled = notificationsEnabled,
                autoStartNext = autoStart
            ),
            completedSessionsToday = streakCount,
            timerModeKey = prefs[KEY_TIMER_MODE_KEY],
            timerActive = prefs[KEY_TIMER_ACTIVE] ?: false,
            timerTimeLeft = prefs[KEY_TIMER_TIME_LEFT] ?: 0,
            timerEndTime = prefs[KEY_TIMER_END_TIME] ?: 0L
        )
    }

    /**
     * Persist the running timer so a session survives the process being killed.
     * [endTime] is an absolute wall-clock timestamp; passing 0 means "not
     * running". Only the transitions are written, never every tick, matching
     * the web app.
     */
    suspend fun saveTimerState(modeKey: String, isActive: Boolean, timeLeft: Int, endTime: Long) {
        context.dataStore.edit {
            it[KEY_TIMER_MODE_KEY] = modeKey
            it[KEY_TIMER_ACTIVE] = isActive
            it[KEY_TIMER_TIME_LEFT] = timeLeft
            it[KEY_TIMER_END_TIME] = endTime
        }
    }

    suspend fun savePreset(preset: TimerPreset) {
        context.dataStore.edit { it[KEY_PRESET] = preset.name }
    }

    suspend fun saveMode(mode: TimerMode) {
        context.dataStore.edit { it[KEY_MODE] = mode.name }
    }

    suspend fun saveCustomDurations(work: Int, breakDuration: Int) {
        context.dataStore.edit {
            it[KEY_CUSTOM_WORK] = work
            it[KEY_CUSTOM_BREAK] = breakDuration
        }
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit {
            it[KEY_SOUND_ENABLED] = settings.soundEnabled
            it[KEY_SOUND_VOLUME] = settings.soundVolume
            it[KEY_HAPTICS_ENABLED] = settings.hapticsEnabled
            it[KEY_NOTIFICATIONS_ENABLED] = settings.notificationsEnabled
            it[KEY_AUTO_START] = settings.autoStartNext
        }
    }

    suspend fun incrementStreak(): Int {
        var newCount = 1
        val today = getTodayDateString()
        context.dataStore.edit { prefs ->
            val lastDate = prefs[KEY_STREAK_DATE] ?: today
            val current = if (lastDate == today) prefs[KEY_STREAK_COUNT] ?: 0 else 0
            newCount = current + 1
            prefs[KEY_STREAK_DATE] = today
            prefs[KEY_STREAK_COUNT] = newCount
        }
        return newCount
    }

    suspend fun resetStreak() {
        context.dataStore.edit {
            it[KEY_STREAK_DATE] = getTodayDateString()
            it[KEY_STREAK_COUNT] = 0
        }
    }
}

data class StoredPreferences(
    val preset: TimerPreset,
    val mode: TimerMode,
    val customWork: Int,
    val customBreak: Int,
    val settings: AppSettings,
    val completedSessionsToday: Int,
    val timerModeKey: String?,
    val timerActive: Boolean,
    val timerTimeLeft: Int,
    val timerEndTime: Long
)
