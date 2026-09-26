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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TimerPreferencesRepository(application)
    private val hapticHelper = HapticHelper(application)
    private val notificationHelper = NotificationHelper(application)

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var targetEndTimeMs: Long = 0L

    init {
        viewModelScope.launch {
            val initial = repository.preferencesFlow.first()
            val totalSeconds = calculateDuration(initial.mode, initial.preset, initial.customWork, initial.customBreak)

            _uiState.update {
                it.copy(
                    mode = initial.mode,
                    preset = initial.preset,
                    customWorkMinutes = initial.customWork,
                    customBreakMinutes = initial.customBreak,
                    timeLeftSeconds = totalSeconds,
                    totalDurationSeconds = totalSeconds,
                    settings = initial.settings,
                    completedSessionsToday = initial.completedSessionsToday
                )
            }
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
        val currentSeconds = _uiState.value.timeLeftSeconds
        if (currentSeconds <= 0) {
            val total = _uiState.value.totalDurationSeconds
            _uiState.update { it.copy(timeLeftSeconds = total) }
        }

        targetEndTimeMs = System.currentTimeMillis() + _uiState.value.timeLeftSeconds * 1000L
        _uiState.update { it.copy(isRunning = true) }

        timerJob = viewModelScope.launch {
            while (_uiState.value.isRunning) {
                val now = System.currentTimeMillis()
                val remainingMs = targetEndTimeMs - now
                val remainingSec = ((remainingMs + 999) / 1000).toInt()

                if (remainingSec <= 0) {
                    _uiState.update { it.copy(timeLeftSeconds = 0, isRunning = false) }
                    handleTimerCompletion()
                    break
                } else {
                    _uiState.update { it.copy(timeLeftSeconds = remainingSec) }
                }
                delay(150)
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isRunning = false) }
    }

    fun resetTimer() {
        hapticHelper.vibrate(HapticType.MEDIUM_CLICK, _uiState.value.settings.hapticsEnabled)
        timerJob?.cancel()
        timerJob = null
        val total = _uiState.value.totalDurationSeconds
        _uiState.update { it.copy(isRunning = false, timeLeftSeconds = total) }
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

        if (autoStart) {
            startTimer()
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

        // Auto transition after 2.5s delay
        viewModelScope.launch {
            delay(2500)
            switchMode(nextMode, autoStart = state.settings.autoStartNext)
        }
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
