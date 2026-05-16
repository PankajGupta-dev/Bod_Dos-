package com.alertnet.bordersentinelalert.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build

object SoundUtils {
    private var mediaPlayer: MediaPlayer? = null

    fun playEmergencyBuzzer(context: Context) {
        // Stop any existing sound
        stopBuzzer()

        // Use ToneGenerator as fallback or MediaPlayer for custom sound
        // For production, you would add an mp3 to res/raw/buzzer.mp3
        try {
            // Using ToneGenerator for immediate results without requiring a resource file
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 5000)
            
            // Continuous Vibration
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
            } else {
                vibrator.vibrate(longArrayOf(0, 500, 200, 500), 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopBuzzer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
