package com.example.player

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Interface representing playback state and event callbacks.
 */
interface PlayerEventListener {
    fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean)
    fun onTracksAvailable(audioTracks: List<String>)
    fun onError(errorCode: Int, message: String)
}

/**
 * Universal interface for modular IPTV stream video players.
 * Adheres to Dependency Inversion Principle (DIP).
 */
interface CustomPlayer {
    /**
     * Initializes the player engine and attaches its rendering surface to the container.
     */
    fun initialize(context: Context, container: ViewGroup)

    /**
     * Obtains the root View representing the video render surface.
     */
    fun getView(): View?

    /**
     * Plays a media stream URL (supports HLS .m3u8, TS, MP4, DASH, etc.).
     */
    fun play(url: String, isHls: Boolean = true)

    /**
     * Pauses the active playback.
     */
    fun pause()

    /**
     * Resumes playback if paused.
     */
    fun resume()

    /**
     * Stops the current playback stream.
     */
    fun stop()

    /**
     * Adjusts the audio volume (0.0f to 1.0f).
     */
    fun setVolume(volume: Float)

    /**
     * Checks if the player is currently playing.
     */
    fun isPlaying(): Boolean

    /**
     * Selects an audio track by index if supported by the engine.
     */
    fun selectAudioTrack(index: Int)

    /**
     * Sets the event listener for player state updates.
     */
    fun setEventListener(listener: PlayerEventListener?)

    /**
     * Releases all codecs, network connections, and system resources.
     */
    fun release()
}
