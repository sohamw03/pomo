package com.pomo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pomo.app.audio.AudioSynthesizer
import com.pomo.app.data.TimerPreferencesRepository
import com.pomo.app.model.TimerMode
import com.pomo.app.model.TimerPreset
import com.pomo.app.model.TimerUiState
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
        /**
         * Linger on 0:00 in the finished phase before crossing over, mirroring
         * the web app's 2.5s setTimeout in handleComplete.
         */
        const val COMPLETION_DELAY_MS = 2500L
    }

    private val repository = TimerPreferencesRepository(application)

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var targetEndTimeMs: Long = 0L
    private val wakeLock = TimerWakeLock(application)
    private var completionJob: Job? = null

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
        if (_uiState.value.isRunning) {
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
                    // Stay "running" through the completion pause so the UI
                    // shows 0:00 with the pause affordance, exactly like the
                    // web app (isActive stays true during its 2.5s delay).
                    _uiState.update { it.copy(timeLeftSeconds = 0) }
                    persistTimerState(active = true, timeLeft = 0)
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
        timerJob?.cancel()
        timerJob = null
        wakeLock.release()
        val total = _uiState.value.totalDurationSeconds
        _uiState.update { it.copy(isRunning = false, timeLeftSeconds = total) }
        persistTimerState(active = false, timeLeft = total)
    }

    fun skipTimer() {
        timerJob?.cancel()
        timerJob = null
        val nextMode = if (_uiState.value.mode == TimerMode.WORK) TimerMode.BREAK else TimerMode.WORK
        switchMode(nextMode, autoStart = true)
    }

    fun setMode(newMode: TimerMode) {
        if (newMode == _uiState.value.mode) return
        switchMode(newMode, autoStart = false)
    }

    private fun switchMode(newMode: TimerMode, autoStart: Boolean) {
        timerJob?.cancel()
        timerJob = null
        wakeLock.release()

        // A pending completion crossover is left alone on purpose: its mode
        // guard no-ops it once the mode has moved on, exactly like the web
        // app never clearing its setTimeout.
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

        // Manual mode changes always stop, because the web app's
        // handleModeChange goes through resetTimer. Only the completion and
        // skip paths pass autoStart = true.
        if (autoStart) {
            startTimer()
        } else {
            persistTimerState(active = false, timeLeft = total)
        }
    }

    fun setPreset(newPreset: TimerPreset) {
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

        // Manual preset changes always stop, because the web app's
        // handlePresetChange goes through resetTimer.
        persistTimerState(active = false, timeLeft = total)
    }

    fun adjustCustomWork(delta: Int) {
        val current = _uiState.value.customWorkMinutes
        val next = (current + delta).coerceIn(1, 999)
        setCustomDurations(next, _uiState.value.customBreakMinutes)
    }

    fun adjustCustomBreak(delta: Int) {
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
            if (_uiState.value.isRunning) {
                // The web app's layout effect restarts a running countdown at
                // the new duration, so re-anchor the deadline instead of
                // keeping the old one.
                _uiState.update { it.copy(totalDurationSeconds = total, timeLeftSeconds = total) }
                startTimer()
            } else {
                _uiState.update { it.copy(totalDurationSeconds = total, timeLeftSeconds = total) }
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

        // Audio alarm, always on like the web app.
        viewModelScope.launch {
            AudioSynthesizer.playAlarm(nextMode, 1.0f)
        }

        // Linger on 0:00 in the finished phase, then cross over — but only if
        // the user has not already moved on manually (e.g. via skip). This is
        // the direct equivalent of the web app's 2.5s setTimeout plus its
        // `currentM === m` guard. The next phase always starts: the web app
        // has no auto-start toggle.
        completionJob?.cancel()
        completionJob = viewModelScope.launch {
            delay(COMPLETION_DELAY_MS)
            if (_uiState.value.mode == completedMode) {
                switchMode(nextMode, autoStart = _uiState.value.isRunning)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        completionJob?.cancel()
        wakeLock.release()
    }
}
