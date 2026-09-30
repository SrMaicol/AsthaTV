package com.example.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Concrete implementation of CustomPlayer using AndroidX Media3 ExoPlayer.
 * Resolves classic IPTV audio issues:
 * 1. AC3/EAC3 Dolby fallback with EXTENSION_RENDERER_MODE_PREFER and decoder fallback.
 * 2. Demuxed HLS streams with setAllowChunklessPreparation(false).
 * 3. AudioManager verification for STREAM_MUSIC unmuting.
 * 4. OkHttpDataSource with ICY headers support for Icecast/Shoutcast IPTV audio streams.
 */
class ExoPlayerImpl : CustomPlayer {

    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var eventListener: PlayerEventListener? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var okHttpDataSourceFactory: OkHttpDataSource.Factory? = null
    private var tvAudioManager: TvAudioManager? = null
    private var appContext: Context? = null

    override fun initialize(context: Context, container: ViewGroup) {
        release()
        this.appContext = context

        // 1. Android TV Audio Focus & Audio Manager Setup
        tvAudioManager = TvAudioManager(
            context = context,
            onAudioFocusLost = {
                exoPlayer?.pause()
            },
            onAudioFocusGained = {
                exoPlayer?.play()
            }
        )
        tvAudioManager?.validateAndPrepareTvAudio()

        // 2. Explicit Media3 AudioAttributes for Android TV & Movie/HDMI output
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // 3. Low-Level AudioSink Factory con Remuestreo PCM 16-bit forzado y AudioSink.Listener
        val renderersFactory = TvAudioSinkFactory.createRenderersFactory(
            context = context,
            onAudioSinkError = {
                exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                    ?.buildUpon()
                    ?.setMaxAudioChannelCount(1) // Fallback a Mono
                    ?.build() ?: return@createRenderersFactory
            }
        )

        // 4. Track selector with Audio Tunneling DISABLED and forced stereo PCM fallback
        trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(false) // Disable hardware audio tunneling for TV speakers
                    .setMaxAudioChannelCount(2) // Force stereo 2.0 PCM downmix
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setPreferredAudioMimeTypes(
                        "audio/eac3",
                        "audio/ac3",
                        "audio/mp4a-latm",
                        "audio/aac",
                        "audio/raw",
                        "audio/mpeg",
                        "audio/mp4"
                    )
            )
        }

        // 5. OkHttpClient configured for ICY 200 OK headers, redirects, and HTTP cleartext
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

        okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Icy-MetaData" to "1",
                    "Connection" to "keep-alive"
                )
            )

        val cacheDataSourceFactory = PlayerCacheManager.createCacheDataSourceFactory(
            context,
            okHttpDataSourceFactory!!
        )

        // 6. MediaSourceFactory using Cached DataSource
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))

        val customLoadControl = PlayerCacheManager.createCustomLoadControl(isLiveStream = true)

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(customLoadControl)
            .setTrackSelector(trackSelector!!)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                volume = 1.0f
                playWhenReady = true
            }

        playerView = (LayoutInflater.from(context).inflate(
            com.example.R.layout.player_view_surface,
            container,
            false
        ) as PlayerView).apply {
            player = exoPlayer
            useController = false
            keepScreenOn = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            (videoSurfaceView as? android.view.SurfaceView)?.apply {
                setZOrderMediaOverlay(false)
            }
        }

        container.removeAllViews()
        container.addView(playerView)

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val isPlaying = exoPlayer?.isPlaying == true
                eventListener?.onPlaybackStateChanged(isPlaying, isBuffering)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val isBuffering = exoPlayer?.playbackState == Player.STATE_BUFFERING
                eventListener?.onPlaybackStateChanged(isPlaying, isBuffering)
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audioTrackTitles = mutableListOf<String>()
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val lang = format.language?.uppercase() ?: "UND"
                            val label = format.label ?: "Pista ${audioTrackTitles.size + 1}"
                            val mime = format.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: ""
                            val chCount = if (format.channelCount > 0) "${format.channelCount}ch" else ""
                            val details = listOf(label, lang, mime, chCount).filter { it.isNotBlank() }.joinToString(" • ")
                            audioTrackTitles.add(details)
                        }
                    }
                }
                eventListener?.onTracksAvailable(audioTrackTitles)
            }

            override fun onPlayerError(error: PlaybackException) {
                eventListener?.onError(error.errorCode, error.localizedMessage ?: "Error de reproducción en ExoPlayer")
            }
        })
    }

    override fun getView(): View? = playerView

    override fun play(url: String, isHls: Boolean) {
        val player = exoPlayer ?: return
        val ctx = appContext ?: return
        try {
            // Request Android TV Audio Focus before playing
            tvAudioManager?.requestAudioFocus()

            player.stop()
            player.clearMediaItems()
            val uri = Uri.parse(url)

            val isHlsLikely = isHls || url.contains(".m3u8", ignoreCase = true) ||
                    url.contains("m3u8", ignoreCase = true) ||
                    url.contains("type=m3u8", ignoreCase = true) ||
                    url.contains("output=m3u8", ignoreCase = true)

            val isTsLikely = url.contains(".ts", ignoreCase = true) ||
                    url.contains("output=ts", ignoreCase = true)

            val dataSourceFactory = PlayerCacheManager.createCacheDataSourceFactory(
                ctx,
                okHttpDataSourceFactory!!
            )

            val mediaSource: androidx.media3.exoplayer.source.MediaSource = when {
                isHlsLikely -> {
                    val hlsExtractorFactory = androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory(
                        androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                        androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM,
                        true
                    )
                    HlsMediaSource.Factory(dataSourceFactory)
                        .setExtractorFactory(hlsExtractorFactory)
                        .setAllowChunklessPreparation(false)
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
                        .createMediaSource(MediaItem.fromUri(uri))
                }
                isTsLikely -> {
                    val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
                        setTsExtractorFlags(
                            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                        )
                    }
                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                        dataSourceFactory,
                        extractorsFactory
                    )
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
                    .createMediaSource(MediaItem.fromUri(uri))
                }
                else -> {
                    DefaultMediaSourceFactory(playerView?.context ?: return)
                        .setDataSourceFactory(dataSourceFactory)
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
                        .createMediaSource(MediaItem.fromUri(uri))
                }
            }

            player.setMediaSource(mediaSource)
            player.volume = 1.0f
            player.prepare()
            player.playWhenReady = true
            player.play()
        } catch (e: Exception) {
            eventListener?.onError(-1, e.message ?: "Error al cargar la URL")
        }
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun resume() {
        tvAudioManager?.requestAudioFocus()
        exoPlayer?.play()
    }

    override fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        tvAudioManager?.abandonAudioFocus()
    }

    override fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0.0f, 1.0f)
    }

    override fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    override fun selectAudioTrack(index: Int) {
        val tracks = exoPlayer?.currentTracks ?: return
        var counter = 0
        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    if (counter == index) {
                        exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                            ?.buildUpon()
                            ?.setOverrideForType(
                                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                            )
                            ?.build() ?: return
                        return
                    }
                    counter++
                }
            }
        }
    }

    override fun setEventListener(listener: PlayerEventListener?) {
        this.eventListener = listener
    }

    override fun release() {
        tvAudioManager?.abandonAudioFocus()
        tvAudioManager = null
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        playerView = null
    }
}
