package com.example.player

import android.content.Context
import android.media.AudioAttributes as FrameworkAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Dedicated Audio Manager for Android TV & Leanback devices.
 * Handles strict TV Audio Focus (AUDIOFOCUS_GAIN), HDMI / ARC routing validation,
 * and silent/mute state resolution before stream playback.
 */
class TvAudioManager(
    private val context: Context,
    private val onAudioFocusLost: () -> Unit,
    private val onAudioFocusGained: () -> Unit
) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager: AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false

    companion object {
        private const val TAG = "TvAudioManager"
    }

    /**
     * Pre-playback validation for Android TV:
     * 1. Checks and un-mutes STREAM_MUSIC
     * 2. Verifies ringerMode is not SILENT
     * 3. Checks available audio output devices (HDMI, ARC, Speaker, Bluetooth)
     */
    fun validateAndPrepareTvAudio() {
        val am = audioManager ?: return
        try {
            // 1. Check Ringer Mode on TV
            if (am.ringerMode == AudioManager.RINGER_MODE_SILENT || am.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                Log.w(TAG, "Android TV in silent/vibrate mode. Setting to RINGER_MODE_NORMAL")
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }

            // 2. Check STREAM_MUSIC volume & mute state
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val isMuted = am.isStreamMute(AudioManager.STREAM_MUSIC)

            if (isMuted || currentVol == 0) {
                val targetVol = (maxVol * 0.75f).toInt().coerceAtLeast(1)
                Log.w(TAG, "STREAM_MUSIC was muted/zero on Android TV. Unmuting and restoring volume to $targetVol/$maxVol")
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
            }

            // 3. Inspect audio devices for HDMI output on Android TV (API 23+)
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                Log.d(TAG, "Detected TV Audio Output: Type=${device.type}, Name=${device.productName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating TV audio status: ${e.message}")
        }
    }

    /**
     * Strictly requests Audio Focus with AUDIOFOCUS_GAIN for Android TV
     * Must be called immediately before loading or playing the stream.
     */
    fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        validateAndPrepareTvAudio()

        return try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val frameworkAudioAttributes = FrameworkAudioAttributes.Builder()
                    .setUsage(FrameworkAudioAttributes.USAGE_MEDIA)
                    .setContentType(FrameworkAudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(frameworkAudioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(this)
                    .build()

                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }

            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            Log.d(TAG, "Audio Focus request for Android TV: Granted=$hasAudioFocus (resultCode=$result)")
            hasAudioFocus
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus on Android TV: ${e.message}")
            false
        }
    }

    /**
     * Releases Audio Focus when stopping or destroying the player.
     */
    fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(this)
            }
            hasAudioFocus = false
            Log.d(TAG, "Audio Focus abandoned on Android TV")
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus: ${e.message}")
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "AudioFocus GAIN on TV")
                hasAudioFocus = true
                onAudioFocusGained()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "AudioFocus PERMANENT LOSS on TV (e.g. Netflix opened)")
                hasAudioFocus = false
                onAudioFocusLost()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.w(TAG, "AudioFocus TRANSIENT LOSS on TV (e.g. Google Assistant voice command)")
                onAudioFocusLost()
            }
        }
    }
}
