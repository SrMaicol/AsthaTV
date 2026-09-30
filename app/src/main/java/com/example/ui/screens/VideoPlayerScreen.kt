package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.LiveChannel
import com.example.player.TvAudioManager
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * REPRODUCTOR ESTABLE DE ALTO RENDIMIENTO (ExoPlayer + FFmpeg software fallback + OkHttp ICY DataSource)
 * Diseñado bajo el patrón de arquitectura nativa para TV / Android:
 * - ExoPlayer encapsulado en 'remember' y 'DisposableEffect' para evitar pérdida de audio en recomposiciones.
 * - Software Fallback activado con EXTENSION_RENDERER_MODE_PREFER (códecs Dolby AC3/EAC3).
 * - HLS con setAllowChunklessPreparation(false) para demux de pistas de audio separadas.
 * - Tunneling desactivado y mezcla forzada a PCM estéreo 2.0.
 * - Volumen forzado a 1.0f y AudioManager unmuted.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    channel: LiveChannel,
    channels: List<LiveChannel>,
    onChannelSelected: (LiveChannel) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isBuffering by remember(channel.id) { mutableStateOf(true) }
    var isPlaying by remember(channel.id) { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var errorMessage by remember(channel.id) { mutableStateOf<String?>(null) }
    var showOverlay by remember { mutableStateOf(true) }
    var showChannelListOverlay by remember { mutableStateOf(false) }
    var audioTracks by remember(channel.id) { mutableStateOf<List<String>>(emptyList()) }
    var selectedAudioIndex by remember(channel.id) { mutableStateOf(0) }
    var currentUrl by remember(channel.id) { mutableStateOf(channel.streamUrl) }
    var autoRetryCount by remember(channel.id) { mutableStateOf(0) }
    var fallbackMode by remember(channel.id) { mutableStateOf(0) }
    var selectedEngineType by remember(channel.id) { mutableIntStateOf(0) }
    var reloadTrigger by remember(channel.id) { mutableStateOf(0) }
    var isSilentReconnect by remember(channel.id) { mutableStateOf(false) }
    var isChangingEngine by remember(channel.id) { mutableStateOf(false) }
    var hasManualEngineSelection by remember(channel.id) { mutableStateOf(false) }

    // OPCIONES AVANZADAS DE REPRODUCTOR
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var selectedSpeed by remember { mutableStateOf(1.0f) }
    var selectedQualityLabel by remember { mutableStateOf("Auto") }
    var volumeBoost by remember { mutableStateOf(1.0f) }

    var showAspectDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }

    // Cooldown al cambiar de motor o canal para permitir que el hardware libere recursos
    LaunchedEffect(selectedEngineType, currentUrl) {
        isChangingEngine = true
        kotlinx.coroutines.delay(1500) // Aumentado a 1.5s para TVs más lentas
        isChangingEngine = false
    }

    // GESTOR DE AUDIOFOCUS DEDICADO PARA ANDROID TV & DISPOSITIVOS MÓVILES
    val tvAudioManager = remember {
        TvAudioManager(
            context = context,
            onAudioFocusLost = {},
            onAudioFocusGained = {}
        )
    }

    // RECURSOS DE RED Y EXTRACCIÓN COMPARTIDOS (Singletons para ahorrar memoria y descriptores)
    val sharedOkHttpDataSourceFactory = remember { com.example.player.PlayerResourceHolder.createDataSourceFactory() }
    val sharedHlsExtractorFactory = remember { com.example.player.PlayerResourceHolder.hlsExtractorFactory }

    // EXOPLAYER DINÁMICO: Se crea solo si es el motor seleccionado, se libera inmediatamente al cambiar
    val exoPlayer = remember(context, selectedEngineType == 0) {
        if (selectedEngineType != 0) return@remember null
        
        tvAudioManager.validateAndPrepareTvAudio()

        // 1. Atributos de Audio de Cine / Media
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        var playerInstance: ExoPlayer? = null

        // 2. Low-Level AudioSink & FFmpeg Software Fallback
        val renderersFactory = com.example.player.TvAudioSinkFactory.createRenderersFactory(
            context = context,
            onAudioSinkError = {
                val player = playerInstance
                if (player != null) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setMaxAudioChannelCount(1)
                        .build()
                }
            }
        )

        // 3. Desactivar Audio Tunneling y forzar estéreo PCM 2.0 para máxima compatibilidad
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(false)
                    .setMaxAudioChannelCount(2)
                    .setExceedRendererCapabilitiesIfNecessary(true)
            )
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(sharedOkHttpDataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))

        // 4. Custom LoadControl optimizado para Android TV
        val customLoadControl = com.example.player.PlayerCacheManager.createCustomLoadControl(isLiveStream = true)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(customLoadControl)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playerInstance = this
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                volume = 1.0f
                playWhenReady = true
            }
    }

    // PlayerView que usa el ExoPlayer dinámico
    val playerView = remember(context, exoPlayer) {
        if (exoPlayer == null) return@remember null
        val view = LayoutInflater.from(context).inflate(
            com.example.R.layout.player_view_surface,
            null,
            false
        ) as PlayerView
        view.apply {
            player = exoPlayer
            useController = false
            keepScreenOn = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    fun triggerSilentEngineFallback() {
        isSilentReconnect = true
        isBuffering = true
        errorMessage = null
        
        // Detener motores de forma agresiva antes de cambiar de estado para liberar hardware
        try {
            exoPlayer?.stop()
            exoPlayer?.setVideoSurface(null)
        } catch (_: Exception) {}

        // Rotar silenciosamente entre los 3 modos de fallback de ExoPlayer (0 -> 1 -> 2 -> 0)
        selectedEngineType = 0
        fallbackMode = (fallbackMode + 1) % 3
        
        coroutineScope.launch {
            delay(500)
            reloadTrigger++
        }
    }

    // DISPOSABLE EFFECT UNIFICADO: Gestiona listeners y limpieza profunda de hardware
    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) return@DisposableEffect onDispose {}
        
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val trackNames = mutableListOf<String>()
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val lang = format.language?.uppercase() ?: "UND"
                            val label = format.label ?: "Pista ${trackNames.size + 1}"
                            val mime = format.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: ""
                            val details = listOf(label, lang, mime).filter { it.isNotBlank() }.joinToString(" • ")
                            trackNames.add(details)
                        }
                    }
                }
                audioTracks = trackNames
            }

            override fun onRenderedFirstFrame() {
                isBuffering = false
                errorMessage = null
                isPlaying = true
                isSilentReconnect = false
                autoRetryCount = 0
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val player = exoPlayer ?: return
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (player.playbackState == Player.STATE_BUFFERING && !player.isPlaying) {
                            isBuffering = true
                        }
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        errorMessage = null
                        isSilentReconnect = false
                        player.volume = if (isMuted) 0.0f else volumeBoost
                        autoRetryCount = 0
                    }
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        isBuffering = false
                        isSilentReconnect = false
                    }
                }
                isPlaying = player.isPlaying
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    isBuffering = false
                    isSilentReconnect = false
                    errorMessage = null
                    autoRetryCount = 0
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val player = exoPlayer ?: return
                
                // 1. Error de Ventana en Vivo (BehindLiveWindow)
                val isBehindLiveWindow = error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                        error.cause?.javaClass?.simpleName?.contains("BehindLiveWindowException", ignoreCase = true) == true ||
                        error.localizedMessage?.contains("BehindLiveWindowException", ignoreCase = true) == true
                
                if (isBehindLiveWindow) {
                    isSilentReconnect = true
                    isBuffering = true
                    try {
                        player.seekToDefaultPosition()
                        player.prepare()
                    } catch (_: Exception) {
                        reloadTrigger++
                    }
                    return
                }

                // 2. Formato no reconocido (Alternar entre 3 niveles de extractores)
                val isUnrecognizedFormat = error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                        error.cause?.javaClass?.simpleName?.contains("UnrecognizedInputFormatException", ignoreCase = true) == true
                
                if (isUnrecognizedFormat && selectedEngineType == 0) {
                    fallbackMode = (fallbackMode + 1) % 3
                    isBuffering = true
                    coroutineScope.launch {
                        delay(300)
                        reloadTrigger++
                    }
                    return
                }

                // 3. Playlist Stuck (HLS)
                val isPlaylistStuck = error.cause?.javaClass?.simpleName?.contains("PlaylistStuckException", ignoreCase = true) == true
                if (isPlaylistStuck) {
                    fallbackMode = (fallbackMode + 1) % 3
                    isSilentReconnect = true
                    isBuffering = true
                    coroutineScope.launch {
                        delay(500)
                        reloadTrigger++
                    }
                    return
                }
                
                if (autoRetryCount < 1) {
                    autoRetryCount++
                    reloadTrigger++
                } else if (!hasManualEngineSelection) {
                    triggerSilentEngineFallback()
                } else {
                    triggerSilentEngineFallback()
                }
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            try {
                // ORDEN CRÍTICO DE SEGURIDAD PARA HARDWARE:
                // 1. Desvincular la vista (evita que el UI intente actualizarse)
                playerView?.player = null
                
                // 2. Detener el reproductor (esto detiene el renderizado de frames)
                if (exoPlayer.playbackState != Player.STATE_IDLE) {
                    exoPlayer.stop()
                }
                
                // 3. Desvincular la superficie de hardware
                exoPlayer.setVideoSurface(null)
                
                // 4. Limpieza final
                tvAudioManager.abandonAudioFocus()
                exoPlayer.removeListener(listener)
                exoPlayer.release()
                Log.d("VideoPlayerScreen", "ExoPlayer Hardware Released Safely")
            } catch (e: Exception) {
                Log.e("VideoPlayerScreen", "Error during safe hardware release: ${e.message}")
            }
        }
    }

    BackHandler {
        if (showEngineDialog) {
            showEngineDialog = false
        } else if (showAspectDialog) {
            showAspectDialog = false
        } else if (showQualityDialog) {
            showQualityDialog = false
        } else if (showAudioDialog) {
            showAudioDialog = false
        } else if (showSpeedDialog) {
            showSpeedDialog = false
        } else if (showChannelListOverlay) {
            showChannelListOverlay = false
        } else if (showOverlay) {
            showOverlay = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(channel) {
        currentUrl = channel.streamUrl
        autoRetryCount = 0
        fallbackMode = 0
        errorMessage = null
        isBuffering = true
        isSilentReconnect = false
        showOverlay = true
        audioTracks = emptyList()
    }

    // CARGA DEL STREAM CON SOPORTE HLS, TS DEMUXED, PROXIES Y AUTO-FALLBACK
    LaunchedEffect(currentUrl, reloadTrigger, selectedEngineType, exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect

        // Detener el reproductor antes de cargar el nuevo stream
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } catch (_: Exception) {}

        // "Quiet Time": Pequeña pausa para permitir que los decodificadores de hardware se estabilicen
        // antes de recibir la nueva carga de trabajo (Previene EGL_BAD_ALLOC en algunos SoCs)
        delay(200)

        if (selectedEngineType != 0) {
            try {
                exoPlayer.setVideoSurface(null)
                playerView?.player = null
            } catch (_: Exception) {}
            return@LaunchedEffect
        }

        // Asegurar vinculación con la vista
        try {
            if (playerView != null && playerView.player != exoPlayer) {
                playerView.player = exoPlayer
            }
        } catch (_: Exception) {}

        isBuffering = true
        errorMessage = null
        try {
            tvAudioManager.requestAudioFocus()
            val uri = Uri.parse(currentUrl)

            val isHlsLikely = currentUrl.contains(".m3u8", ignoreCase = true) ||
                    currentUrl.contains("m3u8", ignoreCase = true) ||
                    currentUrl.contains("type=m3u8", ignoreCase = true) ||
                    currentUrl.contains("output=m3u8", ignoreCase = true)

            val isTsLikely = currentUrl.contains(".ts", ignoreCase = true) ||
                    currentUrl.contains("output=ts", ignoreCase = true)

            val mediaSource: androidx.media3.exoplayer.source.MediaSource = when (fallbackMode) {
                0 -> {
                    HlsMediaSource.Factory(sharedOkHttpDataSourceFactory)
                        .setExtractorFactory(sharedHlsExtractorFactory)
                        .setAllowChunklessPreparation(false)
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
                        .createMediaSource(MediaItem.fromUri(uri))
                }
                1 -> {
                    val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
                        setTsExtractorFlags(
                            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                        )
                    }
                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                        sharedOkHttpDataSourceFactory,
                        extractorsFactory
                    )
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
                    .createMediaSource(MediaItem.fromUri(uri))
                }
                else -> {
                    DefaultMediaSourceFactory(context)
                        .setDataSourceFactory(sharedOkHttpDataSourceFactory)
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
                        .createMediaSource(MediaItem.fromUri(uri))
                }
            }

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.volume = if (isMuted) 0.0f else 1.0f
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } catch (e: Exception) {
            isBuffering = true
            coroutineScope.launch {
                delay(500)
                reloadTrigger++
            }
        }
    }



    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (!isBuffering && errorMessage == null) {
                        player.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { player.pause() } catch (_: Exception) {}
        }
    }

    // Ocultar Overlay automáticamente tras 5 segundos (solo si ningún menú o lista está abierta)
    LaunchedEffect(showOverlay, showChannelListOverlay, showEngineDialog, showAspectDialog, showQualityDialog, showAudioDialog, showSpeedDialog) {
        if (showOverlay && !showChannelListOverlay && !showEngineDialog && !showAspectDialog && !showQualityDialog && !showAudioDialog && !showSpeedDialog) {
            delay(5000)
            showOverlay = false
        }
    }

    // WATCHDOG DE RECONEXIÓN AUTOMÁTICA:
    // Solo actúa tras 3 segundos continuos de buffering real y NUNCA si el usuario está en menús, cambiando canales, o seleccionó el motor manualmente.
    LaunchedEffect(isBuffering, reloadTrigger, showChannelListOverlay, showEngineDialog) {
        if (isBuffering && !showChannelListOverlay && !showEngineDialog && !hasManualEngineSelection) {
            delay(3000) // Esperar 3 segundos continuos antes de considerar timeout
            if (isBuffering && !showChannelListOverlay && !showEngineDialog && !hasManualEngineSelection) {
                // Permitir hasta 10 intentos (2 ciclos completos de todos los motores) para asegurar reconexión
                autoRetryCount = (autoRetryCount + 1) % 10
                triggerSilentEngineFallback()
            }
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val overlayPlayFocusRequester = remember { FocusRequester() }
    val channelListFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(400) // Más tiempo para que la TV asiente la vista
        try {
            rootFocusRequester.requestFocus()
        } catch (e: Exception) {
            Log.e("VideoPlayerScreen", "Error requesting root focus: ${e.message}")
        }
    }

    LaunchedEffect(showOverlay) {
        if (showOverlay && !showChannelListOverlay && !showEngineDialog && !showAspectDialog && !showQualityDialog && !showAudioDialog && !showSpeedDialog) {
            delay(300)
            try {
                overlayPlayFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("VideoPlayerScreen", "Error requesting overlay focus: ${e.message}")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        Key.DirectionCenter.nativeKeyCode,
                        Key.Enter.nativeKeyCode,
                        Key.NumPadEnter.nativeKeyCode -> {
                            if (!showOverlay && !showChannelListOverlay && !showEngineDialog && !showAspectDialog) {
                                showOverlay = true
                                true
                            } else false
                        }
                        Key.DirectionUp.nativeKeyCode,
                        Key.DirectionDown.nativeKeyCode -> {
                            if (!showOverlay && !showChannelListOverlay && !showEngineDialog && !showAspectDialog) {
                                // En Android TV, presionar Arriba o Abajo abre la lista de canales directamente para Zapping rápido
                                showChannelListOverlay = true
                                true
                            } else false
                        }
                        Key.DirectionLeft.nativeKeyCode,
                        Key.DirectionRight.nativeKeyCode -> {
                            if (!showOverlay && !showChannelListOverlay && !showEngineDialog && !showAspectDialog) {
                                showOverlay = true
                                true
                            } else false
                        }
                        Key.MediaPlayPause.nativeKeyCode,
                        Key.MediaPlay.nativeKeyCode,
                        Key.MediaPause.nativeKeyCode -> {
                            if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                            showOverlay = true
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showOverlay = !showOverlay
            }
    ) {
    // SELECCIÓN DE MOTOR DE REPRODUCCIÓN INTERNO (ExoPlayer, LibVLC Nativo, Native VideoView Stagefright)
    Box(modifier = Modifier.fillMaxSize()) {
        key(selectedEngineType) {
            when (selectedEngineType) {
            0 -> {
                // MOTOR 1 & 2: EXOPLAYER MEDIA3 (Nativo Acelerado / IPTV Live)
                if (playerView != null && exoPlayer != null) {
                    AndroidView(
                        factory = {
                            playerView.apply {
                                this.resizeMode = resizeMode
                            }
                        },
                        update = { pv ->
                            if (pv.player != exoPlayer) {
                                pv.player = exoPlayer
                            }
                            if (pv.resizeMode != resizeMode) {
                                pv.resizeMode = resizeMode
                            }
                        },
                        onRelease = { pv ->
                            // IMPORTANTE: Solo desvincular el reproductor de la vista.
                            // NO llamar a stop() ni release() aquí, ya que el ciclo de vida del motor
                            // está gestionado por el DisposableEffect(exoPlayer).
                            try {
                                pv.player = null
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                }
            }
            2 -> {
                // MOTOR 3: LIBVLC NATIVO (VideoLAN)
                LibVlcPlayerView(
                    url = currentUrl,
                    resizeMode = resizeMode,
                    modifier = Modifier.fillMaxSize(),
                    onPrepared = {
                        isBuffering = false
                        isPlaying = true
                        errorMessage = null
                    },
                    onBuffering = { buffering ->
                        isBuffering = buffering
                    },
                    onError = { err ->
                        triggerSilentEngineFallback()
                    }
                )
            }
            else -> {
                // MOTOR 4: ANDROID NATIVE VIDEOVIEW (Stagefright / NuPlayer del OS)
                // Se envuelve en un Box para asegurar que la superficie se limpie correctamente al desmontar
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    mp.start()
                                    isBuffering = false
                                    mp.setOnInfoListener { _, what, _ ->
                                        if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                                            isBuffering = true
                                        } else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                                            isBuffering = false
                                        }
                                        false
                                    }
                                    isPlaying = true
                                    errorMessage = null
                                }
                                setOnErrorListener { _, _, _ ->
                                    triggerSilentEngineFallback()
                                    true
                                }
                                setVideoURI(Uri.parse(currentUrl))
                                tag = "$currentUrl|$reloadTrigger"
                                start()
                            }
                        },
                        update = { vv ->
                            val expectedTag = "$currentUrl|$reloadTrigger"
                            if (vv.tag != expectedTag) {
                                vv.tag = expectedTag
                                vv.setVideoURI(Uri.parse(currentUrl))
                                vv.start()
                            }
                        },
                        onRelease = { vv ->
                            try {
                                vv.stopPlayback()
                                vv.setOnPreparedListener(null)
                                vv.setOnErrorListener(null)
                                vv.setOnInfoListener(null)
                                vv.setVideoURI(null)
                                vv.suspend()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            }
        }

        // Overlay de transición (Cooldown) para ocultar artefactos de BufferQueue mientras el hardware se estabiliza
        if (isChangingEngine) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF38BDF8))
            }
        }
    }

        // INDICADOR DE BUFFERING Y RECONEXIÓN AUTOMÁTICA
        if (isBuffering && !isPlaying && errorMessage == null && !isSilentReconnect) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .background(Color(0xCC0F172A), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(48.dp))
                    Text(
                        text = if (autoRetryCount > 0) "Reconectando señal (intento $autoRetryCount/3)..." else "Conectando stream de audio y video...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }



        // OVERLAY SUPERIOR E INFERIOR CON CONTROLES
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC000000),
                                Color.Transparent,
                                Color(0xDD000000)
                            )
                        )
                    )
            ) {
                // Barra Superior
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .background(if (isBackFocused) Color(0xFFEF4444) else Color(0x66FFFFFF), CircleShape)
                            .size(if (isBackFocused) 50.dp else 44.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    if (channel.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = channel.group,
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }

                    // Botones de Opciones Rápidas
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OptionChipButton(
                            icon = Icons.Default.Tune,
                            label = when {
                                selectedEngineType == 2 -> "Motor: LibVLC Nativo"
                                fallbackMode == 1 -> "Motor: Exo IPTV"
                                else -> "Motor: Exo Nativo"
                            },
                            onClick = { showEngineDialog = true }
                        )

                        OptionChipButton(
                            icon = Icons.Default.AspectRatio,
                            label = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Estirar"
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                                AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> "16:9"
                                else -> "Ajustar"
                            },
                            onClick = { showAspectDialog = true }
                        )

                        OptionChipButton(
                            icon = Icons.Default.HighQuality,
                            label = selectedQualityLabel,
                            onClick = { showQualityDialog = true }
                        )

                        OptionChipButton(
                            icon = Icons.Default.Speed,
                            label = "${selectedSpeed}x",
                            onClick = { showSpeedDialog = true }
                        )

                        if (audioTracks.isNotEmpty()) {
                            OptionChipButton(
                                icon = Icons.Default.GraphicEq,
                                label = "Audio (${audioTracks.size})",
                                onClick = { showAudioDialog = true }
                            )
                        }

                        // Botón de Lista Rápida de Canales
                        var isListFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = {
                                showChannelListOverlay = true
                                showOverlay = false
                            },
                            shape = CircleShape,
                            color = if (isListFocused) Color(0xFF1E3A8A) else Color(0x66FFFFFF),
                            border = BorderStroke(
                                if (isListFocused) 2.dp else 1.dp,
                                if (isListFocused) Color(0xFF38BDF8) else Color(0x33FFFFFF)
                            ),
                            modifier = Modifier
                                .size(44.dp)
                                .onFocusChanged { isListFocused = it.isFocused }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.List,
                                    contentDescription = "Lista de Canales",
                                    tint = if (isListFocused) Color(0xFF38BDF8) else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Barra Inferior de Controles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var isPlayFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                            },
                            modifier = Modifier
                                .focusRequester(overlayPlayFocusRequester)
                                .onFocusChanged { isPlayFocused = it.isFocused }
                                .background(if (isPlayFocused) Color.White else Color(0xFF38BDF8), CircleShape)
                                .size(if (isPlayFocused) 54.dp else 48.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        var isMuteFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer?.volume = if (isMuted) 0.0f else volumeBoost
                            },
                            modifier = Modifier
                                .onFocusChanged { isMuteFocused = it.isFocused }
                                .background(if (isMuteFocused) Color(0xFF1E3A8A) else Color(0x66FFFFFF), CircleShape)
                                .size(if (isMuteFocused) 50.dp else 44.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isMuted) "Activar Sonido" else "Silenciar",
                                tint = if (isMuteFocused) Color(0xFF38BDF8) else (if (isMuted) Color(0xFFEF4444) else Color.White)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Pistas de Audio Disponibles
                        if (audioTracks.isNotEmpty()) {
                            Surface(
                                onClick = { showAudioDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x4438BDF8)
                            ) {
                                Text(
                                    text = "🔊 ${audioTracks.getOrNull(selectedAudioIndex) ?: "Audio Estéreo"}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Insignia EN VIVO - Minimalista, estética y de bajo consumo de recursos
                    Surface(
                        color = Color(0x990F172A),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFEF4444), CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "EN VIVO",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        // DIÁLOGOS DE OPCIONES
        if (showEngineDialog) {
            val currentSelectedIndex = when {
                selectedEngineType == 2 -> 2
                fallbackMode == 1 -> 1
                else -> 0
            }
            OptionSelectorDialog(
                title = "Seleccionar Reproductor / Motor de Video",
                icon = Icons.Default.Tune,
                currentSelectedIndex = currentSelectedIndex,
                options = listOf(
                    "1. ExoPlayer Nativo (Auto HLS/DASH)" to {
                        selectedEngineType = 0
                        fallbackMode = 0
                        isBuffering = true
                        hasManualEngineSelection = true
                        reloadTrigger++
                        Toast.makeText(context, "Activado: ExoPlayer Nativo", Toast.LENGTH_SHORT).show()
                    },
                    "2. ExoPlayer IPTV Live (Demuxing HLS/TS)" to {
                        selectedEngineType = 0
                        fallbackMode = 1
                        isBuffering = true
                        hasManualEngineSelection = true
                        reloadTrigger++
                        Toast.makeText(context, "Activado: ExoPlayer IPTV Live", Toast.LENGTH_SHORT).show()
                    },
                    "3. LibVLC Nativo (VideoLAN SDK)" to {
                        selectedEngineType = 2
                        fallbackMode = 0
                        isBuffering = true
                        hasManualEngineSelection = true
                        reloadTrigger++
                        Toast.makeText(context, "Activado: LibVLC Nativo", Toast.LENGTH_SHORT).show()
                    }
                ),
                onDismiss = { showEngineDialog = false }
            )
        }

        if (showAspectDialog) {
            val currentIdx = when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> 0
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> 1
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> 2
                else -> 3
            }
            OptionSelectorDialog(
                title = "Formato de Pantalla",
                icon = Icons.Default.AspectRatio,
                currentSelectedIndex = currentIdx,
                options = listOf(
                    "Ajustar (Proporción Original)" to { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT },
                    "Estirar a Pantalla Completa" to { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL },
                    "Enfoque / Zoom (Sin Estirar)" to { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM },
                    "Fijo 16:9 Adaptativo" to { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH }
                ),
                onDismiss = { showAspectDialog = false }
            )
        }

        if (showQualityDialog) {
            val qualityIdx = when (selectedQualityLabel) {
                "Auto" -> 0
                "1080p" -> 1
                "720p" -> 2
                "480p" -> 3
                else -> 4
            }
            OptionSelectorDialog(
                title = "Calidad de Video Preferida",
                icon = Icons.Default.HighQuality,
                currentSelectedIndex = qualityIdx,
                options = listOf(
                    "Auto (Adaptativa según red)" to {
                        selectedQualityLabel = "Auto"
                        val player = exoPlayer
                        if (player != null) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE).build()
                        }
                    },
                    "1080p Full HD" to {
                        selectedQualityLabel = "1080p"
                        val player = exoPlayer
                        if (player != null) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setMaxVideoSize(1920, 1080).build()
                        }
                    },
                    "720p HD" to {
                        selectedQualityLabel = "720p"
                        val player = exoPlayer
                        if (player != null) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setMaxVideoSize(1280, 720).build()
                        }
                    },
                    "480p SD" to {
                        selectedQualityLabel = "480p"
                        val player = exoPlayer
                        if (player != null) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setMaxVideoSize(854, 480).build()
                        }
                    },
                    "360p Móvil / Red Lenta" to {
                        selectedQualityLabel = "360p"
                        val player = exoPlayer
                        if (player != null) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setMaxVideoSize(640, 360).build()
                        }
                    }
                ),
                onDismiss = { showQualityDialog = false }
            )
        }

        if (showSpeedDialog) {
            val speedIdx = when (selectedSpeed) {
                0.75f -> 0
                1.0f -> 1
                1.25f -> 2
                1.5f -> 3
                else -> 4
            }
            OptionSelectorDialog(
                title = "Velocidad de Reproducción",
                icon = Icons.Default.Speed,
                currentSelectedIndex = speedIdx,
                options = listOf(
                    "0.75x (Lento)" to {
                        selectedSpeed = 0.75f
                        exoPlayer?.setPlaybackSpeed(0.75f)
                    },
                    "1.0x (Normal)" to {
                        selectedSpeed = 1.0f
                        exoPlayer?.setPlaybackSpeed(1.0f)
                    },
                    "1.25x (Rápido)" to {
                        selectedSpeed = 1.25f
                        exoPlayer?.setPlaybackSpeed(1.25f)
                    },
                    "1.5x (Acelerado)" to {
                        selectedSpeed = 1.5f
                        exoPlayer?.setPlaybackSpeed(1.5f)
                    },
                    "2.0x (Doble)" to {
                        selectedSpeed = 2.0f
                        exoPlayer?.setPlaybackSpeed(2.0f)
                    }
                ),
                onDismiss = { showSpeedDialog = false }
            )
        }

        if (showAudioDialog && audioTracks.isNotEmpty()) {
            OptionSelectorDialog(
                title = "Pistas e Idioma de Audio",
                icon = Icons.Default.GraphicEq,
                currentSelectedIndex = selectedAudioIndex,
                options = audioTracks.mapIndexed { idx, trackName ->
                    trackName to {
                        selectedAudioIndex = idx
                        val player = exoPlayer ?: return@to
                        val tracks = player.currentTracks
                        var counter = 0
                        for (group in tracks.groups) {
                            if (group.type == C.TRACK_TYPE_AUDIO) {
                                for (i in 0 until group.length) {
                                    if (counter == idx) {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(
                                                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                                            )
                                            .build()
                                        return@to
                                    }
                                    counter++
                                }
                            }
                        }
                    }
                },
                onDismiss = { showAudioDialog = false }
            )
        }

        // PANEL LATERAL / MODAL DE CAMBIO RÁPIDO DE CANALES (Zapping)
        AnimatedVisibility(
            visible = showChannelListOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(Color(0xF00F172A))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Canales (${channels.size})",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showChannelListOverlay = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(channels, key = { it.id }, contentType = { "zapping_item" }) { item ->
                            val isSelected = item.id == channel.id
                            ZappingChannelItem(
                                item = item,
                                isSelected = isSelected,
                                onClick = {
                                    onChannelSelected(item)
                                    showChannelListOverlay = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZappingChannelItem(
    item: com.example.model.M3uChannel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = when {
            isFocused -> Color(0xFF1E3A8A)
            isSelected -> Color(0xFF38BDF8).copy(alpha = 0.25f)
            else -> Color(0xFF1E293B)
        },
        border = BorderStroke(
            if (isFocused) 2.dp else 1.dp,
            when {
                isFocused -> Color(0xFF38BDF8)
                isSelected -> Color(0xFF38BDF8).copy(alpha = 0.6f)
                else -> Color(0x22FFFFFF)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.logoUrl.isNotBlank()) {
                val context = LocalContext.current
                val imageRequest = remember(item.logoUrl) {
                    ImageRequest.Builder(context)
                        .data(item.logoUrl)
                        .size(72, 72)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = when {
                        isFocused -> Color.White
                        isSelected -> Color(0xFF38BDF8)
                        else -> Color.White.copy(alpha = 0.9f)
                    },
                    fontSize = 14.sp,
                    fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.group.isNotBlank()) {
                    Text(
                        text = item.group,
                        color = if (isFocused) Color(0xFF93C5FD) else Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isFocused || isSelected) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun OptionChipButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isFocused) Color(0xFF1E3A8A) else Color(0x66FFFFFF),
        border = BorderStroke(
            if (isFocused) 2.dp else 1.dp,
            if (isFocused) Color(0xFF38BDF8) else Color(0x33FFFFFF)
        ),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color(0xFF38BDF8) else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun OptionSelectorDialog(
    title: String,
    icon: ImageVector,
    options: List<Pair<String, () -> Unit>>,
    currentSelectedIndex: Int,
    onDismiss: () -> Unit
) {
    val firstFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(350) // Delay robusto para TVs
        try {
            firstFocusRequester.requestFocus()
        } catch (e: Exception) {
            Log.e("OptionSelectorDialog", "Error requesting focus: ${e.message}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(420.dp)
                .pointerInput(Unit) {}
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = Color(0x33FFFFFF))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    items(options.size) { index ->
                        val (label, onSelect) = options[index]
                        val isSelected = index == currentSelectedIndex
                        var isFocused by remember { mutableStateOf(false) }

                        val backgroundColor = when {
                            isFocused -> Color(0xFF1E3A8A)
                            isSelected -> Color(0xFF38BDF8).copy(alpha = 0.25f)
                            else -> Color(0xFF0F172A)
                        }
                        val borderColor = when {
                            isFocused -> Color(0xFF38BDF8)
                            isSelected -> Color(0xFF38BDF8).copy(alpha = 0.6f)
                            else -> Color(0x22FFFFFF)
                        }

                        Button(
                            onClick = {
                                onSelect()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = backgroundColor,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(if (isFocused) 2.dp else 1.dp, borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .then(
                                    if (index == (if (currentSelectedIndex in options.indices) currentSelectedIndex else 0)) {
                                        Modifier.focusRequester(firstFocusRequester)
                                    } else Modifier
                                )
                                .onFocusChanged { isFocused = it.isFocused },
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    color = if (isFocused || isSelected) Color(0xFF38BDF8) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibVlcPlayerView(
    url: String,
    resizeMode: Int,
    modifier: Modifier = Modifier,
    reloadTrigger: Int = 0,
    onPrepared: () -> Unit = {},
    onBuffering: (Boolean) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext

    // Crear la instancia de LibVLC y MediaPlayer de forma síncrona y estable
    val vlcBundle = remember(appContext) {
        try {
            val options = arrayListOf(
                "-vvv",
                "--http-reconnect",
                "--network-caching=2500",
                "--live-caching=2500",
                "--file-caching=2500",
                "--sout-mux-caching=2500",
                "--http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "--audio-resampler=soxr",
                "--stereo-mode=1",
                "--rtsp-tcp",
                "--no-sub-autodetect-file",
                "--no-spu",
                "--no-osd"
            )
            val vlc = org.videolan.libvlc.LibVLC(appContext, options)
            val player = org.videolan.libvlc.MediaPlayer(vlc)
            Pair(vlc, player)
        } catch (t: Throwable) {
            android.util.Log.e("LibVlcPlayer", "Error initializing LibVLC: ${t.message}", t)
            null
        }
    }

    val libVLC = vlcBundle?.first
    val mediaPlayer = vlcBundle?.second

    if (vlcBundle == null || libVLC == null || mediaPlayer == null) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No se pudo inicializar LibVLC en este dispositivo",
                color = Color.White
            )
        }
        return
    }

    // Liberar recursos de LibVLC únicamente cuando el Composable se destruye
    DisposableEffect(vlcBundle) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
                libVLC.release()
            } catch (_: Throwable) {}
        }
    }

    // Actualizar escala de video
    LaunchedEffect(resizeMode) {
        if (!mediaPlayer.isReleased) {
            try {
                mediaPlayer.videoScale = when (resizeMode) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> org.videolan.libvlc.MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> org.videolan.libvlc.MediaPlayer.ScaleType.SURFACE_FILL
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> org.videolan.libvlc.MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> org.videolan.libvlc.MediaPlayer.ScaleType.SURFACE_16_9
                    else -> org.videolan.libvlc.MediaPlayer.ScaleType.SURFACE_BEST_FIT
                }
            } catch (_: Throwable) {}
        }
    }

    var hasRetriedWithSwDecoder by remember(url, reloadTrigger) { mutableStateOf(false) }

    // Carga y reproducción del stream
    DisposableEffect(url, reloadTrigger, vlcBundle) {
        if (mediaPlayer.isReleased || libVLC.isReleased) {
            return@DisposableEffect onDispose {}
        }

        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }

            val uri = Uri.parse(url)
            val media = org.videolan.libvlc.Media(libVLC, uri).apply {
                setHWDecoderEnabled(true, false)
                addOption(":network-caching=2500")
                addOption(":live-caching=2500")
                addOption(":clock-jitter=0")
                addOption(":clock-synchro=0")
                addOption(":no-spu")
                addOption(":no-osd")
            }

            mediaPlayer.setEventListener { event ->
                when (event.type) {
                    org.videolan.libvlc.MediaPlayer.Event.Buffering -> {
                        onBuffering(event.buffering < 100f)
                    }
                    org.videolan.libvlc.MediaPlayer.Event.Playing -> {
                        onBuffering(false)
                        onPrepared()
                    }
                    org.videolan.libvlc.MediaPlayer.Event.EncounteredError -> {
                        if (!hasRetriedWithSwDecoder && !mediaPlayer.isReleased && !libVLC.isReleased) {
                            hasRetriedWithSwDecoder = true
                            android.util.Log.w("LibVlcPlayer", "Reintentando con decodificación software en LibVLC...")
                            try {
                                mediaPlayer.stop()
                                val swMedia = org.videolan.libvlc.Media(libVLC, uri).apply {
                                    setHWDecoderEnabled(false, false)
                                    addOption(":network-caching=3000")
                                    addOption(":live-caching=3000")
                                    addOption(":no-spu")
                                    addOption(":no-osd")
                                }
                                mediaPlayer.media = swMedia
                                swMedia.release()
                                mediaPlayer.play()
                                return@setEventListener
                            } catch (_: Throwable) {}
                        }
                        onBuffering(false)
                        onError("Error en la reproducción de LibVLC (stream no disponible o formato incompatible)")
                    }
                }
            }

            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
        } catch (t: Throwable) {
            android.util.Log.e("LibVlcPlayer", "Error loading media in LibVLC: ${t.message}", t)
            onError("Error al cargar media en LibVLC: ${t.localizedMessage ?: t.javaClass.simpleName}")
        }

        onDispose {
            try {
                if (!mediaPlayer.isReleased) {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.stop()
                    }
                    mediaPlayer.setEventListener(null)
                }
            } catch (_: Throwable) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            org.videolan.libvlc.util.VLCVideoLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                post {
                    try {
                        if (!mediaPlayer.isReleased) {
                            mediaPlayer.detachViews()
                            mediaPlayer.attachViews(this, null, false, false)
                        }
                    } catch (_: Throwable) {}
                }
            }
        },
        update = { _ ->
            try {
                if (!mediaPlayer.isReleased) {
                    mediaPlayer.videoScale = when (resizeMode) {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> org.videolan.libvlc.MediaPlayer.ScaleType.SURFACE_BEST_FIT
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> org.videolan.libvlc.MediaPlayer.ScaleType.SURFACE_FILL
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> org.videolan.libvlc.MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> org.videolan.libvlc.MediaPlayer.ScaleType.SURFACE_16_9
                        else -> org.videolan.libvlc.MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    }
                }
            } catch (_: Throwable) {}
        },
        onRelease = { _ ->
            try {
                if (!mediaPlayer.isReleased) {
                    mediaPlayer.detachViews()
                }
            } catch (_: Throwable) {}
        },
        modifier = modifier
    )
}
