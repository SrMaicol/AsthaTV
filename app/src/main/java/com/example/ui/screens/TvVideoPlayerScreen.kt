package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.model.M3uChannel
import com.example.player.PlayerEngineType
import kotlinx.coroutines.delay

data class AudioTrackItem(
    val groupIndex: Int,
    val trackIndex: Int,
    val title: String,
    val isSelected: Boolean
)

data class ExternalPlayerApp(
    val packageName: String,
    val activityName: String?,
    val appName: String,
    val isSystemChooser: Boolean = false
)

fun getInstalledExternalPlayers(context: Context): List<ExternalPlayerApp> {
    val list = mutableListOf<ExternalPlayerApp>()
    try {
        val pm = context.packageManager
        val testIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"), "video/*")
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(testIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(testIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val ourPkg = context.packageName
        resolveInfos
            .filter { it.activityInfo != null && it.activityInfo.packageName != ourPkg }
            .forEach { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                if (list.none { it.packageName == pkg }) {
                    list.add(
                        ExternalPlayerApp(
                            packageName = pkg,
                            activityName = resolveInfo.activityInfo.name,
                            appName = label
                        )
                    )
                }
            }
    } catch (_: Exception) {}
    return list
}

fun launchSelectedPlayer(
    context: Context,
    streamUrl: String,
    title: String,
    externalApp: ExternalPlayerApp?
) {
    try {
        val uri = Uri.parse(streamUrl)
        val mimeType = if (streamUrl.contains(".m3u8", ignoreCase = true)) "application/x-mpegURL" else "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            putExtra("title", title)
            putExtra("channel_name", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (externalApp != null && !externalApp.isSystemChooser) {
                if (externalApp.activityName != null) {
                    setClassName(externalApp.packageName, externalApp.activityName)
                } else {
                    setPackage(externalApp.packageName)
                }
            }
        }
        if (externalApp != null && !externalApp.isSystemChooser) {
            context.startActivity(intent)
            Toast.makeText(context, "Abriendo en ${externalApp.appName}...", Toast.LENGTH_SHORT).show()
        } else {
            val chooser = Intent.createChooser(intent, "Seleccionar reproductor de TV")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(streamUrl), "video/*")
                putExtra("title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(fallbackIntent, "Abrir en reproductor de TV"))
        } catch (_: Exception) {
            Toast.makeText(context, "No se encontró ningún reproductor externo compatible", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun TvActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        color = when {
            isFocused -> Color(0xFF0284C7)
            isHighlighted -> Color(0xFF1E3A8A)
            else -> Color(0xFF1E293B)
        },
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) Color(0xFF38BDF8) else Color(0x33FFFFFF)
        ),
        tonalElevation = if (isFocused) 8.dp else 0.dp,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .scale(if (isFocused) 1.08f else 1.0f)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                    (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    onClick()
                    true
                } else false
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color.White else Color(0xFF38BDF8),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
fun TvDialogCard(
    onClick: () -> Unit,
    isSelected: Boolean,
    title: String,
    subtitle: String,
    tag: String? = null,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = when {
            isFocused -> Color(0xFF0284C7)
            isSelected -> Color(0xFF1E3A8A)
            else -> Color(0xFF1E293B)
        },
        border = BorderStroke(
            width = if (isFocused) 3.5.dp else if (isSelected) 1.5.dp else 1.dp,
            color = if (isFocused) Color(0xFF38BDF8) else if (isSelected) Color(0xFF38BDF8) else Color(0x33FFFFFF)
        ),
        tonalElevation = if (isFocused) 10.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .scale(if (isFocused) 1.03f else 1.0f)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                    (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    onClick()
                    true
                } else false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(if (isFocused) Color.White.copy(alpha = 0.2f) else Color(0xFF0F172A), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) Color.White else Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (tag != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isFocused) Color.White.copy(alpha = 0.3f) else Color(0xFF334155)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) Color.White.copy(alpha = 0.9f) else Color(0xFF94A3B8)
                )
            }

            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isFocused) Color.White else Color(0xFF38BDF8),
                    modifier = Modifier.padding(start = 10.dp)
                ) {
                    Text(
                        text = "ACTIVO ✓",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TvDrawerChannelCard(
    channel: M3uChannel,
    isCurrent: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = when {
            isFocused -> Color(0xFF0284C7)
            isCurrent -> Color(0xFF1E3A8A)
            else -> Color(0xFF1E293B)
        },
        border = BorderStroke(
            width = if (isFocused) 3.dp else if (isCurrent) 1.5.dp else 1.dp,
            color = if (isFocused) Color(0xFF38BDF8) else if (isCurrent) Color(0xFF38BDF8) else Color(0x33FFFFFF)
        ),
        tonalElevation = if (isFocused) 8.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .scale(if (isFocused) 1.03f else 1.0f)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                    (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    onClick()
                    true
                } else false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF38BDF8), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFocused) Color.White else if (isCurrent) Color(0xFF38BDF8) else Color.White,
                    maxLines = 1
                )
                Text(
                    text = channel.group,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Gray,
                    maxLines = 1
                )
            }

            if (isCurrent) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isFocused) Color.White else Color(0xFF38BDF8)
                ) {
                    Text(
                        text = "EN VIVO",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TvVideoPlayerScreen(
    channel: M3uChannel,
    channels: List<M3uChannel>,
    onChannelSelected: (M3uChannel) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf(channel.streamUrl) }
    var isBuffering by remember { mutableStateOf(true) }
    var showInfoBanner by remember { mutableStateOf(true) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var drawerSearchQuery by remember { mutableStateOf("") }
    var isMuted by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackItem>>(emptyList()) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var currentEngine by remember { mutableStateOf(PlayerEngineType.EXOPLAYER) }
    var reloadTrigger by remember { mutableStateOf(0) }
    // Modos de pantalla y eliminación de rayas:
    // AspectRatioFrameLayout.RESIZE_MODE_FILL estira a pantalla completa eliminando rayas/franjas negras por defecto en TV
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
    // Superficie nativa SurfaceView para TV (elimina rayitas de entrelazado y sincronización V-Sync)
    var useSurfaceView by remember { mutableStateOf(true) }
    // Botón flotante de menú (las 3 rayitas): Desactivado por defecto para que NO aparezca sobre el video en la TV
    var showFloatingMenuButton by remember { mutableStateOf(false) }

    val externalPlayers = remember { getInstalledExternalPlayers(context) }
    var engineDialogCategoryTab by remember { mutableStateOf(0) }

    fun launchExternalPlayer(streamUrl: String, title: String) {
        launchSelectedPlayer(context, streamUrl, title, null)
    }

    val focusRequester = remember { FocusRequester() }
    val firstButtonFocusRequester = remember { FocusRequester() }
    val engineDialogFirstFocusRequester = remember { FocusRequester() }
    val audioDialogFirstFocusRequester = remember { FocusRequester() }
    val drawerFirstFocusRequester = remember { FocusRequester() }
    val errorRetryFocusRequester = remember { FocusRequester() }
    val drawerListState = rememberLazyListState()

    // Request focus when screen opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(150)
            try {
                errorRetryFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showInfoBanner) {
        if (showInfoBanner) {
            delay(120)
            try {
                firstButtonFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showEngineDialog) {
        if (showEngineDialog) {
            delay(150)
            try {
                engineDialogFirstFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showAudioTrackDialog) {
        if (showAudioTrackDialog) {
            delay(150)
            try {
                audioDialogFirstFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showChannelDrawer) {
        if (showChannelDrawer) {
            val index = channels.indexOfFirst { it.id == channel.id }
            if (index >= 0) {
                try {
                    drawerListState.scrollToItem(index)
                } catch (_: Exception) {}
            }
            delay(150)
            try {
                drawerFirstFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Update currentUrl when channel parameter changes
    LaunchedEffect(channel) {
        currentUrl = channel.streamUrl
        errorMessage = null
        isBuffering = true
        showInfoBanner = true
        audioTracks = emptyList()
    }

    val exoPlayer = remember {
        // Verify and ensure device audio is active
        try {
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null && (audioManager.isStreamMute(android.media.AudioManager.STREAM_MUSIC) || audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0)) {
                val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (maxVol * 0.7f).toInt().coerceAtLeast(1), 0)
            }
        } catch (_: Exception) {}

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
            setEnableAudioTrackPlaybackParams(true)
        }

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setTunnelingEnabled(false)
            )
        }

        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

        val okHttpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Icy-MetaData" to "1",
                    "Connection" to "keep-alive"
                )
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(okHttpDataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(false)
            .build().apply {
                volume = 1.0f
                playWhenReady = true
            }
    }

    LaunchedEffect(currentUrl, currentEngine) {
        if (currentEngine == PlayerEngineType.EXOPLAYER) {
            isBuffering = true
            errorMessage = null
            try {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .build()
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                val uri = Uri.parse(currentUrl)

                if (currentUrl.contains(".m3u8", ignoreCase = true)) {
                    val okHttpClient = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .retryOnConnectionFailure(true)
                        .build()

                    val okHttpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .setDefaultRequestProperties(mapOf("Accept" to "*/*", "Icy-MetaData" to "1", "Connection" to "keep-alive"))

                    val hlsMediaSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(okHttpDataSourceFactory)
                        .setAllowChunklessPreparation(false)
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
                        .createMediaSource(MediaItem.fromUri(uri))

                    exoPlayer.setMediaSource(hlsMediaSource)
                } else {
                    val mediaItem = MediaItem.fromUri(uri)
                    exoPlayer.setMediaItem(mediaItem)
                }

                exoPlayer.volume = if (isMuted) 0.0f else 1.0f
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            } catch (e: Exception) {
                errorMessage = "Error al cambiar de canal: ${e.localizedMessage ?: "Enlace no disponible"}"
                isBuffering = false
            }
        } else {
            // Si el motor actual NO es ExoPlayer (LibVLC, Nativo, App Externa),
            // detenemos ExoPlayer completamente para que no interfiera en audio, red ni lance errores
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (_: Exception) {}
        }
    }

    // Watchdog para cambio automático de reproductor si ExoPlayer tarda más de 6 segundos
    LaunchedEffect(isBuffering, currentEngine, currentUrl) {
        if (isBuffering && currentEngine == PlayerEngineType.EXOPLAYER && errorMessage == null) {
            delay(6000)
            if (isBuffering && currentEngine == PlayerEngineType.EXOPLAYER && errorMessage == null) {
                // Detener ExoPlayer para liberar recursos
                try {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                } catch (_: Exception) {}
                
                currentEngine = PlayerEngineType.LIB_VLC
                Toast.makeText(context, "Carga lenta con ExoPlayer. Cambiando automáticamente a LibVLC...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Buffering timeout watchdog (15 seconds)
    LaunchedEffect(isBuffering, currentUrl) {
        if (isBuffering && errorMessage == null) {
            delay(15000)
            if (isBuffering && errorMessage == null) {
                errorMessage = "Este canal está tardando demasiado en conectar o puede estar fuera de línea."
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val list = mutableListOf<AudioTrackItem>()
                var audioCounter = 1

                for ((groupIndex, group) in tracks.groups.withIndex()) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val mime = format.sampleMimeType ?: ""
                            val lang = format.language?.uppercase() ?: ""
                            val label = format.label ?: ""
                            val mimeShort = mime.substringAfterLast('/').uppercase()
                            val chCount = if (format.channelCount > 0) "${format.channelCount}ch" else ""
                            val details = listOf(label, lang, mimeShort, chCount).filter { it.isNotBlank() }.joinToString(" • ")
                            val trackTitle = if (details.isNotBlank()) "Pista $audioCounter ($details)" else "Pista de audio $audioCounter"
                            val isSelected = group.isTrackSelected(trackIndex)
                            list.add(AudioTrackItem(groupIndex, trackIndex, trackTitle, isSelected))
                            audioCounter++
                        }
                    }
                }
                audioTracks = list
            }

            override fun onPlayerError(error: PlaybackException) {
                // Si el motor actual NO es ExoPlayer, ignorar completamente cualquier error de ExoPlayer
                if (currentEngine != PlayerEngineType.EXOPLAYER) {
                    return
                }
                isBuffering = false
                
                // Fallback automático de motor de reproductor sin cambiar de canal si ExoPlayer falla
                currentEngine = PlayerEngineType.LIB_VLC
                Toast.makeText(context, "Cambiando automáticamente a motor alternativo (LibVLC)...", Toast.LENGTH_SHORT).show()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    errorMessage = null
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Auto-hide info banner after 5 seconds
    LaunchedEffect(showInfoBanner) {
        if (showInfoBanner) {
            delay(5000)
            showInfoBanner = false
        }
    }

    BackHandler {
        when {
            showEngineDialog -> showEngineDialog = false
            showAudioTrackDialog -> showAudioTrackDialog = false
            showChannelDrawer -> showChannelDrawer = false
            showInfoBanner -> showInfoBanner = false
            else -> onBack()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    // Si hay un mensaje de error visible, dejar que los botones de acción del error manejen el D-PAD
                    if (errorMessage != null) {
                        return@onKeyEvent false
                    }
                    // Si algún diálogo de ajustes o audio está abierto, dejar que el diálogo maneje el D-PAD del control remoto
                    if (showEngineDialog || showAudioTrackDialog) {
                        return@onKeyEvent false
                    }
                    // Si el cajón de canales está abierto, permitir cerrarlo con ATRÁS o D-PAD DERECHA
                    if (showChannelDrawer) {
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_BACK,
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                showChannelDrawer = false
                                try { focusRequester.requestFocus() } catch (_: Exception) {}
                                return@onKeyEvent true
                            }
                            else -> return@onKeyEvent false
                        }
                    }
                    if (showInfoBanner) {
                        // Cuando la barra de opciones está visible:
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP,
                            android.view.KeyEvent.KEYCODE_BACK -> {
                                showInfoBanner = false
                                try { focusRequester.requestFocus() } catch (_: Exception) {}
                                true
                            }
                            // ¡IMPORTANTE! NO consumir DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER, ENTER.
                            // Esto permite que el control remoto de la TV navegue entre los botones: Canales, Formato, Motor, App Externa, etc.
                            else -> false
                        }
                    } else {
                        // Cuando la barra está oculta (modo video pantalla completa):
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                            android.view.KeyEvent.KEYCODE_INFO -> {
                                showInfoBanner = true
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                            android.view.KeyEvent.KEYCODE_MENU -> {
                                showChannelDrawer = !showChannelDrawer
                                true
                            }
                            android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                            android.view.KeyEvent.KEYCODE_PAGE_UP,
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                if (channels.isNotEmpty()) {
                                    val currentIndex = channels.indexOfFirst { it.id == channel.id }
                                    val nextIndex = if (currentIndex < channels.size - 1) currentIndex + 1 else 0
                                    onChannelSelected(channels[nextIndex])
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
                            android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
                                if (channels.isNotEmpty()) {
                                    val currentIndex = channels.indexOfFirst { it.id == channel.id }
                                    val prevIndex = if (currentIndex > 0) currentIndex - 1 else channels.size - 1
                                    onChannelSelected(channels[prevIndex])
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                exoPlayer.pause()
                                true
                            }
                            else -> false
                        }
                    }
                } else {
                    false
                }
            }
            .clickable {
                if (showChannelDrawer) {
                    showChannelDrawer = false
                } else {
                    showInfoBanner = !showInfoBanner
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val screenWidth = maxWidth
        val isCompactScreen = screenWidth < 600.dp
        val drawerWidth = if (isCompactScreen) (screenWidth * 0.88f) else 380.dp

        when (currentEngine) {
            PlayerEngineType.EXOPLAYER -> {
                AndroidView(
                    factory = { ctx ->
                        val layoutRes = if (useSurfaceView) {
                            com.example.R.layout.player_view_surface
                        } else {
                            com.example.R.layout.player_view_texture
                        }
                        val view = LayoutInflater.from(ctx).inflate(
                            layoutRes,
                            null,
                            false
                        ) as PlayerView
                        view.apply {
                            player = exoPlayer
                            useController = false
                            keepScreenOn = true
                            this.resizeMode = resizeMode
                            (videoSurfaceView as? android.view.SurfaceView)?.apply {
                                setZOrderMediaOverlay(false)
                            }
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
                        }
                        if (playerView.resizeMode != resizeMode) {
                            playerView.resizeMode = resizeMode
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            PlayerEngineType.LIB_VLC -> {
                LibVlcPlayerView(
                    url = currentUrl,
                    resizeMode = resizeMode,
                    reloadTrigger = reloadTrigger,
                    modifier = Modifier.fillMaxSize(),
                    onPrepared = {
                        isBuffering = false
                        errorMessage = null
                    },
                    onBuffering = { buffering ->
                        isBuffering = buffering
                    },
                    onError = { err ->
                        errorMessage = err
                        isBuffering = false
                    }
                )
            }
            PlayerEngineType.NATIVE -> {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            keepScreenOn = true
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
                            }
                            setOnErrorListener { _, what, extra ->
                                isBuffering = false
                                errorMessage = "Error en reproductor nativo del sistema (código: $what, extra: $extra)"
                                true
                            }
                            val headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                "Accept" to "*/*"
                            )
                            setVideoURI(Uri.parse(currentUrl), headers)
                            start()
                        }
                    },
                    update = { vv ->
                        // VideoView update
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            PlayerEngineType.EXTERNAL_APP -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "Reproduciendo en Reproductor Externo",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Canal: ${channel.name}\nPuedes reproducir en VLC, MX Player, Kodi u otra app de tu TV.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvActionButton(
                                onClick = { launchExternalPlayer(currentUrl, channel.name) },
                                icon = Icons.Default.OpenInNew,
                                label = "Elegir App Externa"
                            )
                            TvActionButton(
                                onClick = { currentEngine = PlayerEngineType.EXOPLAYER },
                                icon = Icons.Default.SwapHoriz,
                                label = "Volver a ExoPlayer"
                            )
                        }
                    }
                }
            }
        }

        // Buffering / Loading Spinner
        if (isBuffering && errorMessage == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )
            }
        }

        // Botón flotante de menú (3 rayitas) - OCULTO por defecto en TV para eliminar las rayitas de la pantalla
        if (showFloatingMenuButton && showInfoBanner && errorMessage == null && !showChannelDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(if (isCompactScreen) 16.dp else 24.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = { showChannelDrawer = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0x990F172A), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Lista de Canales",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Bottom OSD Channel Info Banner
        if (showInfoBanner && errorMessage == null && !showChannelDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(if (isCompactScreen) 16.dp else 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(if (screenWidth > 1100.dp) 0.85f else 1f)
                        .heightIn(min = 76.dp, max = 94.dp)
                        .graphicsLayer {
                            clip = true
                            shape = RoundedCornerShape(16.dp)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xCC0F172A),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (channel.logoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = channel.logoUrl,
                                    contentDescription = channel.name,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0xFF38BDF8), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LiveTv,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Categoría: ${channel.group}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Botón Canales (Focusable con control remoto de TV)
                            TvActionButton(
                                onClick = { showChannelDrawer = true },
                                icon = Icons.AutoMirrored.Filled.List,
                                label = "Canales (◄)",
                                focusRequester = firstButtonFocusRequester
                            )

                            // 2. Botón Formato de Pantalla (Eliminar rayas negras/franjas)
                            val modeLabel = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Original"
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Estirar (Sin rayas)"
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom (Sin rayas)"
                                AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> "16:9 TV"
                                else -> "Formato"
                            }
                            TvActionButton(
                                onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    val desc = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Ajustar (Original)"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Estirar a pantalla completa (Sin rayas)"
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom (Sin rayas)"
                                        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> "16:9 TV"
                                        else -> "Ajustar"
                                    }
                                    Toast.makeText(context, "Modo: $desc", Toast.LENGTH_SHORT).show()
                                },
                                icon = Icons.Default.AspectRatio,
                                label = modeLabel
                            )

                            // 3. Botón Motor / Reproductor (Seleccionar cualquier motor de video)
                            TvActionButton(
                                onClick = { showEngineDialog = true },
                                icon = Icons.Default.SwapHoriz,
                                label = "Motor: ${currentEngine.shortName}"
                            )

                            // 4. Abrir en App Externa (VLC, MX Player, etc.)
                            TvActionButton(
                                onClick = { launchExternalPlayer(currentUrl, channel.name) },
                                icon = Icons.Default.OpenInNew,
                                label = "App Externa"
                            )

                            // 5. Selector de pistas de audio
                            if (currentEngine == PlayerEngineType.EXOPLAYER && audioTracks.isNotEmpty()) {
                                TvActionButton(
                                    onClick = { showAudioTrackDialog = true },
                                    icon = Icons.Default.Audiotrack,
                                    label = "Audio"
                                )
                            }

                            // 6. Botón Silenciar / Activar audio
                            TvActionButton(
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0.0f else 1.0f
                                },
                                icon = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                label = if (isMuted) "Silenciado" else "Audio ON"
                            )

                            // LIVE Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEF4444)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                    Text(
                                        text = "EN VIVO",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right-side Channel Selector Drawer
        AnimatedVisibility(
            visible = showChannelDrawer,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .background(Color(0xFF0F172A))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(if (isCompactScreen) 16.dp else 24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header with Title and Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Lista de Canales",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        IconButton(onClick = { showChannelDrawer = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Search field inside drawer
                    OutlinedTextField(
                        value = drawerSearchQuery,
                        onValueChange = { drawerSearchQuery = it },
                        placeholder = { Text("Buscar canal...", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val filteredChannels = if (drawerSearchQuery.isBlank()) {
                        channels
                    } else {
                        channels.filter { it.name.contains(drawerSearchQuery, ignoreCase = true) }
                    }

                    LazyColumn(
                        state = drawerListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredChannels) { ch ->
                            val isCurrent = ch.id == channel.id
                            TvDrawerChannelCard(
                                channel = ch,
                                isCurrent = isCurrent,
                                onClick = {
                                    showChannelDrawer = false
                                    onChannelSelected(ch)
                                },
                                focusRequester = if (isCurrent) drawerFirstFocusRequester else null
                            )
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Error de Reproducción",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.Red
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Canal: ${channel.name}\nURL: $currentUrl",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    TvActionButton(
                        onClick = {
                            errorMessage = null
                            isBuffering = true
                            reloadTrigger++
                            if (currentEngine == PlayerEngineType.EXOPLAYER) {
                                exoPlayer.seekToDefaultPosition()
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                            }
                        },
                        icon = Icons.Default.Refresh,
                        label = "Reintentar",
                        isHighlighted = true,
                        focusRequester = errorRetryFocusRequester
                    )

                    TvActionButton(
                        onClick = {
                            showEngineDialog = true
                        },
                        icon = Icons.Default.Settings,
                        label = "Cambiar Motor"
                    )

                    TvActionButton(
                        onClick = {
                            launchExternalPlayer(currentUrl, channel.name)
                        },
                        icon = Icons.Default.OpenInNew,
                        label = "Abrir en App Externa"
                    )

                    TvActionButton(
                        onClick = {
                            errorMessage = null
                            if (channels.isNotEmpty()) {
                                val currentIndex = channels.indexOfFirst { it.id == channel.id }
                                val nextIndex = if (currentIndex < channels.size - 1) currentIndex + 1 else 0
                                onChannelSelected(channels[nextIndex])
                            }
                        },
                        icon = Icons.Default.SkipNext,
                        label = "Siguiente Canal"
                    )

                    TvActionButton(
                        onClick = {
                            onBack()
                        },
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "Volver"
                    )
                }
            }
        }

        // Dialog de Ajustes de Pantalla, Motores de Video y Anti-Rayas
        if (showEngineDialog) {
            AlertDialog(
                onDismissRequest = { showEngineDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .widthIn(min = 400.dp, max = 780.dp)
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.88f),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Motores de Video y Pantalla (TV)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Usa el control remoto (D-PAD ▲ ▼ y OK) para seleccionar opciones",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Filtro de categorías / Pestañas superiores accesibles con D-PAD
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val tabs = listOf("Todo", "Motores y Apps", "Formato Pantalla", "Superficie")
                            tabs.forEachIndexed { index, tabName ->
                                val isSelectedTab = engineDialogCategoryTab == index
                                TvActionButton(
                                    onClick = { engineDialogCategoryTab = index },
                                    icon = when (index) {
                                        1 -> Icons.Default.SwapHoriz
                                        2 -> Icons.Default.AspectRatio
                                        3 -> Icons.Default.Tv
                                        else -> Icons.Default.Settings
                                    },
                                    label = tabName,
                                    isHighlighted = isSelectedTab
                                )
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // SECCIÓN 1: MOTOR DE VIDEO Y REPRODUCTORES (INTERNOS Y EXTERNOS)
                            if (engineDialogCategoryTab == 0 || engineDialogCategoryTab == 1) {
                                item {
                                    Text(
                                        text = "1. Motores de Reproducción y Reproductores:",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // ExoPlayer
                                        TvDialogCard(
                                            onClick = {
                                                currentEngine = PlayerEngineType.EXOPLAYER
                                                errorMessage = null
                                                isBuffering = true
                                                Toast.makeText(context, "Motor: ExoPlayer (Media3)", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = currentEngine == PlayerEngineType.EXOPLAYER,
                                            title = "ExoPlayer (Google Media3)",
                                            subtitle = "Motor oficial de Google • Aceleración por hardware, desentrelazado nativo y sincronización V-Sync recomendada para TV.",
                                            tag = "Recomendado TV",
                                            icon = Icons.Default.Bolt,
                                            focusRequester = engineDialogFirstFocusRequester
                                        )

                                        // LibVLC
                                        TvDialogCard(
                                            onClick = {
                                                currentEngine = PlayerEngineType.LIB_VLC
                                                errorMessage = null
                                                isBuffering = true
                                                reloadTrigger++
                                                Toast.makeText(context, "Motor: LibVLC (VideoLAN)", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = currentEngine == PlayerEngineType.LIB_VLC,
                                            title = "LibVLC (VideoLAN SDK)",
                                            subtitle = "VideoLAN SDK • Máxima tolerancia a códecs complejos, pistas de audio problemáticas o cortes de red.",
                                            tag = "VLC Core",
                                            icon = Icons.Default.PlayCircle
                                        )

                                        // Reproductor Nativo del Sistema
                                        TvDialogCard(
                                            onClick = {
                                                currentEngine = PlayerEngineType.NATIVE
                                                errorMessage = null
                                                isBuffering = true
                                                Toast.makeText(context, "Motor: Reproductor Nativo", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = currentEngine == PlayerEngineType.NATIVE,
                                            title = "Reproductor Nativo (Android OS)",
                                            subtitle = "Decodificador nativo estándar del firmware del televisor Android (MediaPlayer / VideoView).",
                                            tag = "Sistema",
                                            icon = Icons.Default.Tv
                                        )

                                        // Selector del Sistema
                                        TvDialogCard(
                                            onClick = {
                                                showEngineDialog = false
                                                launchSelectedPlayer(context, currentUrl, channel.name, null)
                                            },
                                            isSelected = false,
                                            title = "Selector del Sistema (Cualquier App Externa)",
                                            subtitle = "Abre el menú de Android TV para seleccionar cualquier otro reproductor de video.",
                                            tag = "Selector TV",
                                            icon = Icons.Default.OpenInNew
                                        )

                                        // Apps externas detectadas en el televisor (VLC, MX Player, Kodi, etc.)
                                        if (externalPlayers.isNotEmpty()) {
                                            externalPlayers.forEach { extApp ->
                                                TvDialogCard(
                                                    onClick = {
                                                        showEngineDialog = false
                                                        launchSelectedPlayer(context, currentUrl, channel.name, extApp)
                                                    },
                                                    isSelected = false,
                                                    title = "Abrir en ${extApp.appName}",
                                                    subtitle = "Reproducir este canal directamente en la aplicación externa ${extApp.appName} instalada en tu televisor.",
                                                    tag = "App Instalada",
                                                    icon = Icons.Default.OpenInNew
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // SECCIÓN 2: FORMATO DE PANTALLA (ELIMINAR RAYAS NEGRAS / BARRAS)
                            if (engineDialogCategoryTab == 0 || engineDialogCategoryTab == 2) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "2. Formato de Pantalla (Eliminar Rayas Negras y Franjas):",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TvDialogCard(
                                            onClick = {
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                Toast.makeText(context, "Estirar a Pantalla Completa (Sin Rayas)", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL,
                                            title = "Estirar a Pantalla Completa (Sin Rayas)",
                                            subtitle = "Llena el 100% de la pantalla de tu televisor eliminando cualquier franja negra lateral o superior.",
                                            tag = "Sin Rayas",
                                            icon = Icons.Default.AspectRatio
                                        )

                                        TvDialogCard(
                                            onClick = {
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                Toast.makeText(context, "Zoom / Recortar (Sin Rayas)", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                            title = "Zoom / Recortar (Sin Rayas)",
                                            subtitle = "Llena toda la pantalla manteniendo la proporción original de la imagen sin deformar.",
                                            tag = "Sin Franjas",
                                            icon = Icons.Default.AspectRatio
                                        )

                                        TvDialogCard(
                                            onClick = {
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                                Toast.makeText(context, "16:9 Panorámico TV", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
                                            title = "16:9 Panorámico TV",
                                            subtitle = "Fuerza la relación de aspecto panorámica 16:9 estándar de Smart TV.",
                                            tag = "16:9",
                                            icon = Icons.Default.AspectRatio
                                        )

                                        TvDialogCard(
                                            onClick = {
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                Toast.makeText(context, "Original / Ajustar", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                            title = "Original / Proporción Nativa",
                                            subtitle = "Muestra la relación de aspecto original de emisión del canal (puede mostrar barras si difiere).",
                                            tag = "Nativo",
                                            icon = Icons.Default.AspectRatio
                                        )
                                    }
                                }
                            }

                            // SECCIÓN 3: RENDERIZADOR DE SUPERFICIE (ANTI-RAYITAS DE ENTRELAZADO)
                            if (engineDialogCategoryTab == 0 || engineDialogCategoryTab == 3) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "3. Superficie de Video (Anti-Rayitas de Entrelazado y Tearing):",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TvDialogCard(
                                            onClick = {
                                                useSurfaceView = true
                                                Toast.makeText(context, "SurfaceView Nativo TV (Recomendado)", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = useSurfaceView,
                                            title = "SurfaceView Nativo de TV (Recomendado)",
                                            subtitle = "Elimina rayitas horizontales de entrelazado por hardware y sincroniza con el refresco V-Sync del televisor.",
                                            tag = "Recomendado TV",
                                            icon = Icons.Default.Tv
                                        )

                                        TvDialogCard(
                                            onClick = {
                                                useSurfaceView = false
                                                Toast.makeText(context, "TextureView", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = !useSurfaceView,
                                            title = "TextureView (Modo Alternativo)",
                                            subtitle = "Usa composición por textura de OpenGL.",
                                            tag = "Alternativo",
                                            icon = Icons.Default.Tv
                                        )
                                    }
                                }
                            }

                            // SECCIÓN 4: BOTÓN FLOTANTE DE MENÚ (LAS 3 RAYITAS)
                            if (engineDialogCategoryTab == 0) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "4. Botón Flotante de Menú (3 Rayitas):",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TvDialogCard(
                                            onClick = {
                                                showFloatingMenuButton = false
                                                Toast.makeText(context, "Botón de menú oculto", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = !showFloatingMenuButton,
                                            title = "Oculto (Recomendado para TV)",
                                            subtitle = "Pantalla 100% limpia sin botones flotantes sobre el video. Usa tu control remoto (◄ o Menú).",
                                            tag = "Limpio",
                                            icon = Icons.Default.LiveTv
                                        )

                                        TvDialogCard(
                                            onClick = {
                                                showFloatingMenuButton = true
                                                Toast.makeText(context, "Botón de menú visible con controles", Toast.LENGTH_SHORT).show()
                                            },
                                            isSelected = showFloatingMenuButton,
                                            title = "Mostrar botón de 3 rayitas",
                                            subtitle = "Muestra el botón flotante ☰ únicamente cuando la barra de controles esté activa.",
                                            tag = "Visible",
                                            icon = Icons.Default.Menu
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TvActionButton(
                        onClick = { showEngineDialog = false },
                        icon = Icons.Default.Check,
                        label = "Listo / Cerrar",
                        isHighlighted = true
                    )
                },
                containerColor = Color(0xFF0F172A)
            )
        }

        if (showAudioTrackDialog) {
            AlertDialog(
                onDismissRequest = { showAudioTrackDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 650.dp)
                    .fillMaxWidth(0.85f),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Pistas de Audio Disponibles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                text = {
                    if (audioTracks.isEmpty()) {
                        Text(
                            text = "No se detectaron pistas de audio adicionales en este canal.",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(audioTracks) { index, track ->
                                TvDialogCard(
                                    onClick = {
                                        val tracks = exoPlayer.currentTracks
                                        if (track.groupIndex in 0 until tracks.groups.size) {
                                            val group = tracks.groups[track.groupIndex]
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(
                                                    TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
                                                )
                                                .build()
                                        }
                                        showAudioTrackDialog = false
                                        Toast.makeText(context, "Pista seleccionada: ${track.title}", Toast.LENGTH_SHORT).show()
                                    },
                                    isSelected = track.isSelected,
                                    title = track.title,
                                    subtitle = if (track.isSelected) "Pista de audio activa actualmente" else "Pista de audio alternativa",
                                    icon = Icons.Default.Audiotrack,
                                    focusRequester = if (index == 0) audioDialogFirstFocusRequester else null
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TvActionButton(
                        onClick = { showAudioTrackDialog = false },
                        icon = Icons.Default.Close,
                        label = "Cerrar"
                    )
                },
                containerColor = Color(0xFF0F172A)
            )
        }
    }
}
