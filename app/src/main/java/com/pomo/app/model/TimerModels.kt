package com.pomo.app.model

enum class TimerMode {
    WORK,
    BREAK;

    val isWork: Boolean
        get() = this == WORK
}

enum class TimerPreset {
    P_25_5,
    P_50_10,
    CUSTOM
}

data class AppSettings(
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 0.8f,
    val hapticsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = false,
    // The web app has no such setting: it always rolls straight into the next
    // phase. Defaulting to true keeps the ported app behaving the same way out
    // of the box; the toggle is there to opt out.
    val autoStartNext: Boolean = true
)

data class TimerUiState(
    val mode: TimerMode = TimerMode.WORK,
    val preset: TimerPreset = TimerPreset.P_25_5,
    val customWorkMinutes: Int = 25,
    val customBreakMinutes: Int = 5,
    val timeLeftSeconds: Int = 25 * 60,
    val totalDurationSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val isCustomExpanded: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val completedSessionsToday: Int = 0,
    val settings: AppSettings = AppSettings()
) {
    val timeFormatted: String
        get() {
            val mins = timeLeftSeconds / 60
            val secs = timeLeftSeconds % 60
            return "%02d:%02d".format(mins, secs)
        }

    val progress: Float
        get() = if (totalDurationSeconds > 0) {
            (timeLeftSeconds.toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f
}
