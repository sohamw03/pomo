package com.pomo.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.pomo.app.model.TimerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sin

object AudioSynthesizer {
    private const val SAMPLE_RATE = 44100

    suspend fun playAlarm(nextMode: TimerMode, volume: Float = 0.8f) = withContext(Dispatchers.Default) {
        val vol = volume.coerceIn(0f, 1f)
        if (vol <= 0f) return@withContext

        if (nextMode == TimerMode.BREAK) {
            // Ascending double chime: G5 (783.99 Hz) -> C6 (1046.50 Hz)
            val note1 = generateTone(783.99, 1.5, 0.5f * vol)
            val note2 = generateTone(1046.50, 2.0, 0.5f * vol)
            val combined = mixTones(listOf(
                ToneEntry(note1, 0),
                ToneEntry(note2, (0.2 * SAMPLE_RATE).toInt())
            ))
            playPcm(combined)
        } else {
            // Repeating alert bell: A5 (880 Hz), A5 (880 Hz)
            val note1 = generateTone(880.00, 1.2, 0.4f * vol)
            val note2 = generateTone(880.00, 1.5, 0.4f * vol)
            val combined = mixTones(listOf(
                ToneEntry(note1, 0),
                ToneEntry(note2, (0.15 * SAMPLE_RATE).toInt())
            ))
            playPcm(combined)
        }
    }

    private data class ToneEntry(val samples: ShortArray, val offsetSamples: Int)

    private fun generateTone(frequency: Double, durationSec: Double, peakVolume: Float): ShortArray {
        val numSamples = (durationSec * SAMPLE_RATE).toInt()
        val samples = ShortArray(numSamples)
        val attackSamples = (0.02 * SAMPLE_RATE).toInt()
        // Matches the web exponentialRampToValueAtTime(0.001, start + duration):
        // value(k) = peak * (0.001 / peak)^(k / tail), hitting exactly 0.001
        // at the last sample.
        val tailSamples = (numSamples - attackSamples).toDouble()
        val decayBase = 0.001 / peakVolume.toDouble()

        for (i in 0 until numSamples) {
            val time = i.toDouble() / SAMPLE_RATE
            val rawSine = sin(2.0 * Math.PI * frequency * time)

            // Envelope: linear ramp up for attack, smooth exponential decay
            val gain = if (i < attackSamples) {
                (i.toFloat() / attackSamples) * peakVolume
            } else {
                val k = (i - attackSamples).toDouble() / tailSamples
                (peakVolume * decayBase.pow(k)).toFloat()
            }

            val sampleVal = (rawSine * gain * Short.MAX_VALUE).toInt()
            samples[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    private fun mixTones(entries: List<ToneEntry>): ShortArray {
        var totalLength = 0
        for (entry in entries) {
            val end = entry.offsetSamples + entry.samples.size
            if (end > totalLength) totalLength = end
        }

        val mixed = ShortArray(totalLength)
        val accum = IntArray(totalLength)

        for (entry in entries) {
            for (i in entry.samples.indices) {
                accum[entry.offsetSamples + i] += entry.samples[i].toInt()
            }
        }

        for (i in 0 until totalLength) {
            mixed[i] = accum[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return mixed
    }

    private fun playPcm(pcm: ShortArray) {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufSize, pcm.size * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()

            // Release after playing
            Thread.sleep((pcm.size * 1000L) / SAMPLE_RATE + 200)
            track.stop()
            track.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
