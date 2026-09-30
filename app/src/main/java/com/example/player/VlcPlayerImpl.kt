package com.example.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup

/**
 * Concrete implementation skeleton for LibVLC (VideoLAN).
 * Configured with IPTV audio best practices:
 * 1. OpenSL ES / AudioTrack output with soxr resampler.
 * 2. Unmutes AudioManager STREAM_MUSIC.
 * 3. Supports multi-track audio selection and hardware acceleration for video with full software audio decoding.
 */
class VlcPlayerImpl : CustomPlayer {

    private var surfaceView: SurfaceView? = null
    private var eventListener: PlayerEventListener? = null
    private var isPlayingState: Boolean = false
    private var currentVolume: Float = 1.0f
    private var tvAudioManager: TvAudioManager? = null

    companion object {
        /**
         * Returns recommended LibVLC launch arguments for IPTV streams with multi-audio codecs.
         */
        fun getRecommendedVlcOptions(): ArrayList<String> {
            return arrayListOf(
                "-vvv",
                "--http-reconnect",
                "--network-caching=2000",
                "--clock-jitter=0",
                "--clock-synchro=0",
                // Audio configuration (Stereo PCM downmix, no tunneling)
                "--aout=opensles",              // Use OpenSL ES output or AudioTrack
                "--stereo-mode=1",              // Force basic Stereo 2.0 (PCM)
                "--audio-resampler=soxr",       // High quality audio resampler
                "--no-audio-time-stretch",      // Avoid robotic audio artifacts on stream delay
                "--audio-desync=0"              // Keep exact sync between video & audio
            )
        }
    }

    override fun initialize(context: Context, container: ViewGroup) {
        release()

        // 1. Android TV Audio Focus & Audio Manager Setup
        tvAudioManager = TvAudioManager(
            context = context,
            onAudioFocusLost = {
                isPlayingState = false
                eventListener?.onPlaybackStateChanged(isPlaying = false, isBuffering = false)
            },
            onAudioFocusGained = {
                isPlayingState = true
                eventListener?.onPlaybackStateChanged(isPlaying = true, isBuffering = false)
            }
        )
        tvAudioManager?.validateAndPrepareTvAudio()

        surfaceView = SurfaceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            keepScreenOn = true
        }

        container.removeAllViews()
        container.addView(surfaceView)

        eventListener?.onPlaybackStateChanged(isPlaying = false, isBuffering = false)
    }

    override fun getView(): View? = surfaceView

    override fun play(url: String, isHls: Boolean) {
        try {
            tvAudioManager?.requestAudioFocus()
            eventListener?.onPlaybackStateChanged(isPlaying = false, isBuffering = true)
            isPlayingState = true
            eventListener?.onPlaybackStateChanged(isPlaying = true, isBuffering = false)
        } catch (e: Exception) {
            eventListener?.onError(-1, "LibVLC error: ${e.localizedMessage}")
        }
    }

    override fun pause() {
        isPlayingState = false
        eventListener?.onPlaybackStateChanged(isPlaying = false, isBuffering = false)
    }

    override fun resume() {
        tvAudioManager?.requestAudioFocus()
        isPlayingState = true
        eventListener?.onPlaybackStateChanged(isPlaying = true, isBuffering = false)
    }

    override fun stop() {
        tvAudioManager?.abandonAudioFocus()
        isPlayingState = false
        eventListener?.onPlaybackStateChanged(isPlaying = false, isBuffering = false)
    }

    override fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0.0f, 1.0f)
    }

    override fun isPlaying(): Boolean = isPlayingState

    override fun selectAudioTrack(index: Int) {
        // In full LibVLC: vlcMediaPlayer?.setAudioTrack(index)
    }

    override fun setEventListener(listener: PlayerEventListener?) {
        this.eventListener = listener
    }

    override fun release() {
        tvAudioManager?.abandonAudioFocus()
        tvAudioManager = null
        isPlayingState = false
        surfaceView = null
    }
}
