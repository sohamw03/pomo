package com.pomo.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pomo.app.model.TimerMode
import com.pomo.app.model.TimerPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pomo_preferences")

class TimerPreferencesRepository(private val context: Context) {
    companion object {
        private val KEY_PRESET = stringPreferencesKey("timer_preset")
        private val KEY_MODE = stringPreferencesKey("timer_mode")
        private val KEY_CUSTOM_WORK = intPreferencesKey("custom_work")
        private val KEY_CUSTOM_BREAK = intPreferencesKey("custom_break")

        // Running-timer state, mirroring the web app's `pomo_timer_state`
        // localStorage entry: { modeKey, isActive, timeLeft, endTime }.
        private val KEY_TIMER_MODE_KEY = stringPreferencesKey("timer_state_mode")
        private val KEY_TIMER_ACTIVE = booleanPreferencesKey("timer_state_active")
        private val KEY_TIMER_TIME_LEFT = intPreferencesKey("timer_state_time_left")
        private val KEY_TIMER_END_TIME = longPreferencesKey("timer_state_end_time")
    }

    val preferencesFlow: Flow<StoredPreferences> = context.dataStore.data.map { prefs ->
        val presetStr = prefs[KEY_PRESET] ?: TimerPreset.P_25_5.name
        val modeStr = prefs[KEY_MODE] ?: TimerMode.WORK.name
        val customWork = prefs[KEY_CUSTOM_WORK] ?: 25
        val customBreak = prefs[KEY_CUSTOM_BREAK] ?: 5

        StoredPreferences(
            preset = try { TimerPreset.valueOf(presetStr) } catch (e: Exception) { TimerPreset.P_25_5 },
            mode = try { TimerMode.valueOf(modeStr) } catch (e: Exception) { TimerMode.WORK },
            customWork = customWork,
            customBreak = customBreak,
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
}

data class StoredPreferences(
    val preset: TimerPreset,
    val mode: TimerMode,
    val customWork: Int,
    val customBreak: Int,
    val timerModeKey: String?,
    val timerActive: Boolean,
    val timerTimeLeft: Int,
    val timerEndTime: Long
)
