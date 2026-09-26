package com.pomo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pomo.app.audio.AudioSynthesizer
import com.pomo.app.data.TimerPreferencesRepository
import com.pomo.app.haptics.HapticHelper
import com.pomo.app.haptics.HapticType
import com.pomo.app.model.AppSettings
import com.pomo.app.model.TimerMode
import com.pomo.app.model.TimerPreset
import com.pomo.app.model.TimerUiState
import com.pomo.app.notification.NotificationHelper
import com.pomo.app.timer.TimerWakeLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        /** Countdown refresh interval, matching the web app's 100ms tick. */
        const val TICK_MS = 100L
    }

    private val repository = TimerPreferencesRepository(application)
    private val hapticHelper = HapticHelper(application)
    private val notificationHelper = NotificationHelper(application)

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var targetEndTimeMs: Long = 0L
    private val wakeLock = TimerWakeLock(application)

    init {
        viewModelScope.launch {
            val initial = repository.preferencesFlow.first()
            val totalSeconds = calculateDuration(initial.mode, initial.preset, initial.customWork, initial.customBreak)

            // Mirrors the web app's loadInitialState(): a saved timer is only
            // restored when it belongs to the mode currently in effect.
            var timeLeft = totalSeconds
            var running = false
            var endTime = 0L
            if (initial.timerModeKey != null && initial.timerModeKey == initial.mode.name) {
                if (initial.timerActive && initial.timerEndTime > 0L) {
                    val remaining = Math.round(
                        (initial.timerEndTime - System.currentTimeMillis()) / 1000.0
                    ).toInt()
                    timeLeft = maxOf(0, remaining)
                    running = true
                    endTime = initial.timerEndTime
                } else {
                    timeLeft = initial.timerTimeLeft
                }
            }

            _uiState.update {
                it.copy(
                    mode = initial.mode,
                    preset = initial.preset,
                    customWorkMinutes = initial.customWork,
                    customBreakMinutes = initial.customBreak,
                    timeLeftSeconds = timeLeft,
                    totalDurationSeconds = totalSeconds,
                    settings = initial.settings,
                    completedSessionsToday = initial.completedSessionsToday,
                    isRunning = running
                )
            }

            if (running) {
                // Resume against the original deadline rather than restarting,
                // so a session that was killed 20 minutes in still ends on time.
                targetEndTimeMs = endTime
                beginTicking()
            }
        }
    }

    /** Persist the running timer. Only called on transitions, never per tick. */
    private fun persistTimerState(active: Boolean, timeLeft: Int) {
        val modeKey = _uiState.value.mode.name
        val endTime = if (active) targetEndTimeMs else 0L
        viewModelScope.launch {
            repository.saveTimerState(modeKey, active, timeLeft, endTime)
        }
    }

    private fun calculateDuration(
        mode: TimerMode,
        preset: TimerPreset,
        customWork: Int,
        customBreak: Int
    ): Int {
        val minutes = when (preset) {
            TimerPreset.P_25_5 -> if (mode == TimerMode.WORK) 25 else 5
            TimerPreset.P_50_10 -> if (mode == TimerMode.WORK) 50 else 10
            TimerPreset.CUSTOM -> if (mode == TimerMode.WORK) customWork else customBreak
        }
        return minutes * 60
    }

    fun togglePlayPause() {
        val currentState = _uiState.value
        hapticHelper.vibrate(HapticType.HEAVY_CLICK, currentState.settings.hapticsEnabled)

        if (currentState.isRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        var current = _uiState.value.timeLeftSeconds
        if (current <= 0) {
            current = _uiState.value.totalDurationSeconds
        }

        targetEndTimeMs = System.currentTimeMillis() + current * 1000L
        _uiState.update { it.copy(timeLeftSeconds = current, isRunning = true) }
        persistTimerState(active = true, timeLeft = current)
        beginTicking()
    }

    /** Drives the countdown against [targetEndTimeMs] until paused or finished. */
    private fun beginTicking() {
        timerJob?.cancel()
        wakeLock.acquire()
        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRunning) {
                val remainingMs = targetEndTimeMs - System.currentTimeMillis()
                // Round, not ceil, to match the web app's
                // Math.round((endTime - now) / 1000).
                val remainingSec = Math.round(remainingMs / 1000.0).toInt()

                if (remainingSec <= 0) {
                    _uiState.update { it.copy(timeLeftSeconds = 0, isRunning = false) }
                    wakeLock.release()
                    persistTimerState(active = false, timeLeft = 0)
                    handleTimerCompletion()
                    break
                } else {
                    _uiState.update { it.copy(timeLeftSeconds = remainingSec) }
                }
                delay(TICK_MS)
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        wakeLock.release()
        val remaining = _uiState.value.timeLeftSeconds
        _uiState.update { it.copy(isRunning = false) }
        persistTimerState(active = false, timeLeft = remaining)
    }

    fun resetTimer() {
        hapticHelper.vibrate(HapticType.MEDIUM_CLICK, _uiState.value.settings.hapticsEnabled)
        timerJob?.cancel()
        timerJob = null
        wakeLock.release()
        val total = _uiState.value.totalDurationSeconds
        _uiState.update { it.copy(isRunning = false, timeLeftSeconds = total) }
        persistTimerState(active = false, timeLeft = total)
    }

    fun skipTimer() {
        hapticHelper.vibrate(HapticType.MEDIUM_CLICK, _uiState.value.settings.hapticsEnabled)
        timerJob?.cancel()
        timerJob = null
        val nextMode = if (_uiState.value.mode == TimerMode.WORK) TimerMode.BREAK else TimerMode.WORK
        switchMode(nextMode, autoStart = true)
    }

    fun setMode(newMode: TimerMode) {
        if (newMode == _uiState.value.mode) return
        hapticHelper.vibrate(HapticType.SELECTION_CLICK, _uiState.value.settings.hapticsEnabled)
        switchMode(newMode, autoStart = false)
    }

    private fun switchMode(newMode: TimerMode, autoStart: Boolean) {
        timerJob?.cancel()
        timerJob = null
        wakeLock.release()

        val wasRunning = _uiState.value.isRunning
        val total = calculateDuration(newMode, _uiState.value.preset, _uiState.value.customWorkMinutes, _uiState.value.customBreakMinutes)

        _uiState.update {
            it.copy(
                mode = newMode,
                totalDurationSeconds = total,
                timeLeftSeconds = total,
                isRunning = false
            )
        }

        viewModelScope.launch {
            repository.saveMode(newMode)
        }

        // The web app restarts the countdown at the new duration and keeps it
        // running if it was already running. `autoStart` covers the completion
        // and skip paths, where the next phase starts regardless.
        if (wasRunning || autoStart) {
            startTimer()
        } else {
            persistTimerState(active = false, timeLeft = total)
        }
    }

    fun setPreset(newPreset: TimerPreset) {
        hapticHelper.vibrate(HapticType.SELECTION_CLICK, _uiState.value.settings.hapticsEnabled)

        if (newPreset == TimerPreset.CUSTOM) {
            if (_uiState.value.preset == TimerPreset.CUSTOM) {
                _uiState.update { it.copy(isCustomExpanded = !it.isCustomExpanded) }
                return
            } else {
                _uiState.update { it.copy(preset = TimerPreset.CUSTOM, isCustomExpanded = true) }
            }
        } else {
            _uiState.update { it.copy(preset = newPreset, isCustomExpanded = false) }
        }

        timerJob?.cancel()
        timerJob = null
        wakeLock.release()
        val wasRunning = _uiState.value.isRunning
        val total = calculateDuration(_uiState.value.mode, newPreset, _uiState.value.customWorkMinutes, _uiState.value.customBreakMinutes)

        _uiState.update {
            it.copy(
                totalDurationSeconds = total,
                timeLeftSeconds = total,
                isRunning = false
            )
        }

        viewModelScope.launch {
            repository.savePreset(newPreset)
        }

        // Same rule as switchMode: changing the duration mid-session keeps the
        // countdown running from the new total.
        if (wasRunning) {
            startTimer()
        } else {
            persistTimerState(active = false, timeLeft = total)
        }
    }

    fun adjustCustomWork(delta: Int) {
        hapticHelper.vibrate(HapticType.LIGHT_TICK, _uiState.value.settings.hapticsEnabled)
        val current = _uiState.value.customWorkMinutes
        val next = (current + delta).coerceIn(1, 999)
        setCustomDurations(next, _uiState.value.customBreakMinutes)
    }

    fun adjustCustomBreak(delta: Int) {
        hapticHelper.vibrate(HapticType.LIGHT_TICK, _uiState.value.settings.hapticsEnabled)
        val current = _uiState.value.customBreakMinutes
        val next = (current + delta).coerceIn(1, 999)
        setCustomDurations(_uiState.value.customWorkMinutes, next)
    }

    fun setCustomDurations(work: Int, breakDuration: Int) {
        val nextWork = work.coerceIn(1, 999)
        val nextBreak = breakDuration.coerceIn(1, 999)

        _uiState.update {
            it.copy(
                customWorkMinutes = nextWork,
                customBreakMinutes = nextBreak
            )
        }

        if (_uiState.value.preset == TimerPreset.CUSTOM) {
            val total = calculateDuration(_uiState.value.mode, TimerPreset.CUSTOM, nextWork, nextBreak)
            _uiState.update { it.copy(totalDurationSeconds = total, timeLeftSeconds = total) }
            // Only meaningful while paused; a running timer keeps its deadline.
            if (!_uiState.value.isRunning) {
                persistTimerState(active = false, timeLeft = total)
            }
        }

        viewModelScope.launch {
            repository.saveCustomDurations(nextWork, nextBreak)
        }
    }

    private fun handleTimerCompletion() {
        val state = _uiState.value
        val completedMode = state.mode
        val nextMode = if (completedMode == TimerMode.WORK) TimerMode.BREAK else TimerMode.WORK

        // Haptic pulse & Audio Alarm
        hapticHelper.vibrate(HapticType.ALARM_PULSE, state.settings.hapticsEnabled)

        if (state.settings.soundEnabled) {
            viewModelScope.launch {
                AudioSynthesizer.playAlarm(nextMode, state.settings.soundVolume)
            }
        }

        // System Notification
        notificationHelper.showCompletionNotification(completedMode, state.settings.notificationsEnabled)

        // Streak Count update if Work session completed
        if (completedMode == TimerMode.WORK) {
            viewModelScope.launch {
                val newStreak = repository.incrementStreak()
                _uiState.update { it.copy(completedSessionsToday = newStreak) }
            }
        }

        // Advance to the next phase. The web app switches immediately and only
        // if the user has not already changed mode manually (e.g. via skip).
        if (_uiState.value.mode == completedMode) {
            switchMode(nextMode, autoStart = state.settings.autoStartNext)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        wakeLock.release()
    }

    fun setSettingsOpen(isOpen: Boolean) {
        hapticHelper.vibrate(HapticType.SELECTION_CLICK, _uiState.value.settings.hapticsEnabled)
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun updateSettings(newSettings: AppSettings) {
        _uiState.update { it.copy(settings = newSettings) }
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }

    fun resetStreak() {
        hapticHelper.vibrate(HapticType.MEDIUM_CLICK, _uiState.value.settings.hapticsEnabled)
        viewModelScope.launch {
            repository.resetStreak()
            _uiState.update { it.copy(completedSessionsToday = 0) }
        }
    }

    fun testAudioAlarm() {
        viewModelScope.launch {
            AudioSynthesizer.playAlarm(TimerMode.BREAK, _uiState.value.settings.soundVolume)
        }
    }
}
