package com.example.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.VideoView

/**
 * Concrete implementation of CustomPlayer using Android's native MediaPlayer / VideoView.
 * Provides fallback for legacy codecs (MPEG-L2, MPEG-2 TS) supported by the OS.
 */
class NativePlayerImpl : CustomPlayer {

    private var videoView: VideoView? = null
    private var mediaPlayer: MediaPlayer? = null
    private var eventListener: PlayerEventListener? = null
    private var isPlayingState: Boolean = false
    private var currentVolume: Float = 1.0f

    override fun initialize(context: Context, container: ViewGroup) {
        release()

        videoView = VideoView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            keepScreenOn = true

            setOnPreparedListener { mp ->
                mediaPlayer = mp
                mp.isLooping = true
                mp.setVolume(currentVolume, currentVolume)
                mp.start()
                isPlayingState = true
                eventListener?.onPlaybackStateChanged(isPlaying = true, isBuffering = false)
            }

            setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                        eventListener?.onPlaybackStateChanged(isPlaying = isPlayingState, isBuffering = true)
                    }
                    MediaPlayer.MEDIA_INFO_BUFFERING_END, MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                        eventListener?.onPlaybackStateChanged(isPlaying = isPlayingState, isBuffering = false)
                    }
                }
                true
            }

            setOnErrorListener { _, what, extra ->
                isPlayingState = false
                eventListener?.onError(what, "Error en reproductor nativo del sistema (código: $what, extra: $extra)")
                true
            }
        }

        container.removeAllViews()
        container.addView(videoView)
    }

    override fun getView(): View? = videoView

    override fun play(url: String, isHls: Boolean) {
        val view = videoView ?: return
        try {
            eventListener?.onPlaybackStateChanged(isPlaying = false, isBuffering = true)
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept" to "*/*"
            )
            view.setVideoURI(Uri.parse(url), headers)
            view.start()
        } catch (e: Exception) {
            eventListener?.onError(-1, e.message ?: "Error al reproducir en reproductor nativo")
        }
    }

    override fun pause() {
        videoView?.pause()
        isPlayingState = false
        eventListener?.onPlaybackStateChanged(isPlaying = false, isBuffering = false)
    }

    override fun resume() {
        videoView?.start()
        isPlayingState = true
        eventListener?.onPlaybackStateChanged(isPlaying = true, isBuffering = false)
    }

    override fun stop() {
        videoView?.stopPlayback()
        isPlayingState = false
        eventListener?.onPlaybackStateChanged(isPlaying = false, isBuffering = false)
    }

    override fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0.0f, 1.0f)
        mediaPlayer?.setVolume(currentVolume, currentVolume)
    }

    override fun isPlaying(): Boolean = isPlayingState

    override fun selectAudioTrack(index: Int) {
        // Native MediaPlayer supports track selection via selectTrack() on API 16+
        mediaPlayer?.let { mp ->
            val trackInfo = mp.trackInfo
            var audioIndex = 0
            for (i in trackInfo.indices) {
                if (trackInfo[i].trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                    if (audioIndex == index) {
                        mp.selectTrack(i)
                        break
                    }
                    audioIndex++
                }
            }
        }
    }

    override fun setEventListener(listener: PlayerEventListener?) {
        this.eventListener = listener
    }

    override fun release() {
        videoView?.stopPlayback()
        videoView = null
        mediaPlayer = null
        isPlayingState = false
    }
}
