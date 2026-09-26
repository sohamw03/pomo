package com.pomo.app.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticType {
    LIGHT_TICK,
    SELECTION_CLICK,
    MEDIUM_CLICK,
    HEAVY_CLICK,
    ALARM_PULSE
}

class HapticHelper(private val context: Context) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun vibrate(type: HapticType, enabled: Boolean = true) {
        if (!enabled || vibrator == null || !vibrator!!.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (type) {
                    HapticType.LIGHT_TICK ->
                        VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticType.SELECTION_CLICK ->
                        VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticType.MEDIUM_CLICK ->
                        VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticType.HEAVY_CLICK ->
                        VibrationEffect.createOneShot(55, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticType.ALARM_PULSE -> {
                        val timings = longArrayOf(0, 150, 100, 150, 100, 300)
                        val amplitudes = intArrayOf(0, 200, 0, 200, 0, 255)
                        VibrationEffect.createWaveform(timings, amplitudes, -1)
                    }
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (type) {
                    HapticType.LIGHT_TICK -> vibrator?.vibrate(10)
                    HapticType.SELECTION_CLICK -> vibrator?.vibrate(20)
                    HapticType.MEDIUM_CLICK -> vibrator?.vibrate(35)
                    HapticType.HEAVY_CLICK -> vibrator?.vibrate(55)
                    HapticType.ALARM_PULSE -> vibrator?.vibrate(longArrayOf(0, 150, 100, 150, 100, 300), -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
