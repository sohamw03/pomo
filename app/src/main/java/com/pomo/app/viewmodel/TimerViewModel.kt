package com.pomo.app.viewmodel

import android.app.Application
import android.os.SystemClock
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
    /**
     * Monotonic deadline while running (elapsedRealtime base, immune to wall-clock
     * jumps). Display is derived from it every tick: ceil(remainingMs / 1000), so
     * each displayed second lasts exactly 1000ms.
     */
    private var targetEndElapsedMs: Long = 0L
    /** Wall-clock deadline, persisted only so a killed session can be restored. */
    private var targetEndWallMs: Long = 0L
    /** Exact ms remaining at pause/init. Source of truth for resume (no rounding loss). */
    private var remainingMsExact: Long = 0L
    private val wakeLock = TimerWakeLock(application)
    private var completionJob: Job? = null

    private fun ceilSeconds(remainingMs: Long): Int =
        if (remainingMs <= 0L) 0 else ((remainingMs + 999L) / 1000L).toInt()

    init {
        viewModelScope.launch {
            val initial = repository.preferencesFlow.first()
            val totalSeconds = calculateDuration(initial.mode, initial.preset, initial.customWork, initial.customBreak)

            // Mirrors the web app's loadInitialState(): a saved timer is only
            // restored when it belongs to the mode currently in effect.
            var timeLeft = totalSeconds
            var running = false
            var remainingMs = totalSeconds * 1000L
            var endWall = 0L
            if (initial.timerModeKey != null && initial.timerModeKey == initial.mode.name) {
                if (initial.timerActive && initial.timerEndTime > 0L) {
                    // Resume against the original wall-clock deadline (the only
                    // thing that survives process death), then re-anchor a
                    // monotonic deadline from it.
                    remainingMs = maxOf(0L, initial.timerEndTime - System.currentTimeMillis())
                    timeLeft = ceilSeconds(remainingMs)
                    running = remainingMs > 0L
                    endWall = if (running) initial.timerEndTime else 0L
                    if (!running) remainingMs = 0L
                } else if (!initial.timerActive) {
                    remainingMs = if (initial.timerRemainingMs >= 0L) {
                        initial.timerRemainingMs
                    } else {
                        // Migration from pre-remainingMs saves (rounded seconds only).
                        maxOf(0, initial.timerTimeLeft) * 1000L
                    }
                    timeLeft = ceilSeconds(remainingMs)
                }
            }

            remainingMsExact = remainingMs
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
                // Re-anchor monotonic + wall deadlines from the restored position
                // so a session that was killed 20 minutes in still ends on time.
                targetEndElapsedMs = SystemClock.elapsedRealtime() + remainingMs
                targetEndWallMs = endWall
                beginTicking()
            }
        }
    }

    /** Persist the timer. Only called on transitions, never per tick. */
    private fun persistTimerState(active: Boolean, timeLeft: Int, remainingMs: Long) {
        val modeKey = _uiState.value.mode.name
        val endTime = if (active) targetEndWallMs else 0L
        viewModelScope.launch {
            repository.saveTimerState(modeKey, active, timeLeft, remainingMs, endTime)
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
        var remainingMs = remainingMsExact
        // Fresh start (or finished 0:00): use the full duration.
        // Otherwise resume from the exact paused position, not the rounded display value.
        if (current <= 0 || remainingMs <= 0L || ceilSeconds(remainingMs) != current) {
            if (current <= 0 || remainingMs <= 0L) {
                current = _uiState.value.totalDurationSeconds
                remainingMs = current * 1000L
            } else {
                // Display was changed while paused (e.g. duration edited):
                // remainingMsExact was already synced by the editor path.
                remainingMs = remainingMsExact
            }
        }

        remainingMsExact = remainingMs
        targetEndElapsedMs = SystemClock.elapsedRealtime() + remainingMs
        targetEndWallMs = System.currentTimeMillis() + remainingMs
        _uiState.update { it.copy(timeLeftSeconds = ceilSeconds(remainingMs), isRunning = true) }
        persistTimerState(active = true, timeLeft = ceilSeconds(remainingMs), remainingMs = remainingMs)
        beginTicking()
    }

    /** Drives the countdown against [targetEndElapsedMs] until paused or finished. */
    private fun beginTicking() {
        timerJob?.cancel()
        wakeLock.acquire()
        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRunning) {
                val remainingMs = targetEndElapsedMs - SystemClock.elapsedRealtime()
                val remainingSec = ceilSeconds(remainingMs)

                if (remainingSec <= 0) {
                    // Stay "running" through the completion pause so the UI
                    // shows 0:00 with the pause affordance, exactly like the
                    // web app (isActive stays true during its 2.5s delay).
                    remainingMsExact = 0L
                    _uiState.update { it.copy(timeLeftSeconds = 0) }
                    persistTimerState(active = true, timeLeft = 0, remainingMs = 0L)
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
        val remainingMs = maxOf(0L, targetEndElapsedMs - SystemClock.elapsedRealtime())
        remainingMsExact = remainingMs
        val secs = ceilSeconds(remainingMs)
        targetEndElapsedMs = 0L
        targetEndWallMs = 0L
        _uiState.update { it.copy(isRunning = false, timeLeftSeconds = secs) }
        persistTimerState(active = false, timeLeft = secs, remainingMs = remainingMs)
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        wakeLock.release()
        val total = _uiState.value.totalDurationSeconds
        remainingMsExact = total * 1000L
        targetEndElapsedMs = 0L
        targetEndWallMs = 0L
        _uiState.update { it.copy(isRunning = false, timeLeftSeconds = total) }
        persistTimerState(active = false, timeLeft = total, remainingMs = remainingMsExact)
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

        remainingMsExact = total * 1000L
        targetEndElapsedMs = 0L
        targetEndWallMs = 0L
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
            persistTimerState(active = false, timeLeft = total, remainingMs = remainingMsExact)
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

        remainingMsExact = total * 1000L
        targetEndElapsedMs = 0L
        targetEndWallMs = 0L
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
        persistTimerState(active = false, timeLeft = total, remainingMs = remainingMsExact)
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
                // Restart the running countdown at the new duration with exact ms.
                remainingMsExact = total * 1000L
                targetEndElapsedMs = SystemClock.elapsedRealtime() + remainingMsExact
                targetEndWallMs = System.currentTimeMillis() + remainingMsExact
                _uiState.update { it.copy(totalDurationSeconds = total, timeLeftSeconds = total) }
                persistTimerState(active = true, timeLeft = total, remainingMs = remainingMsExact)
                beginTicking()
            } else {
                remainingMsExact = total * 1000L
                targetEndElapsedMs = 0L
                targetEndWallMs = 0L
                _uiState.update { it.copy(totalDurationSeconds = total, timeLeftSeconds = total) }
                persistTimerState(active = false, timeLeft = total, remainingMs = remainingMsExact)
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
