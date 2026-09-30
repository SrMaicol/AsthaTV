package com.example

import android.content.Context
import android.media.AudioAttributes as FrameworkAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.TvChannelCatalogScreen
import com.example.ui.screens.TvVideoPlayerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TvM3uViewModel
import com.example.viewmodel.UiState

class MainActivity : ComponentActivity(), AudioManager.OnAudioFocusChangeListener {

    private val viewModel: TvM3uViewModel by viewModels()
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        private const val TAG = "MainActivity_Audio"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Formato opaco para decodificación de video en TV sin bandas ni artefactos
        window.setFormat(android.graphics.PixelFormat.OPAQUE)

        // Dirige los botones físicos del control remoto a STREAM_MUSIC
        volumeControlStream = AudioManager.STREAM_MUSIC
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        setContent {
            MyApplicationTheme {
                // Recolectar estados de forma consciente del ciclo de vida
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
                val favorites by viewModel.favorites.collectAsStateWithLifecycle()
                val history by viewModel.watchHistory.collectAsStateWithLifecycle()
                val profiles by viewModel.userProfiles.collectAsStateWithLifecycle()
                val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
                val testResult by viewModel.testResult.collectAsStateWithLifecycle()
                val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
                ) {
                    val activeChannel = currentChannel
                    if (activeChannel != null) {
                        val channelsList = (uiState as? UiState.Success)?.channels ?: emptyList()
                        TvVideoPlayerScreen(
                            channel = activeChannel,
                            channels = channelsList,
                            onChannelSelected = { channel -> viewModel.selectChannel(channel) },
                            onBack = { viewModel.clearCurrentChannel() }
                        )
                    } else {
                        when (val state = uiState) {
                            is UiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                                }
                            }
                            is UiState.Success -> {
                                TvChannelCatalogScreen(
                                    channels = state.channels,
                                    favorites = favorites,
                                    history = history,
                                    profiles = profiles,
                                    activeProfile = activeProfile,
                                    currentChannel = currentChannel,
                                    searchQuery = searchQuery,
                                    onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                                    selectedTab = selectedTab,
                                    onTabSelected = { viewModel.setSelectedTab(it) },
                                    onChannelSelected = { channel -> viewModel.selectChannel(channel) },
                                    onToggleFavorite = { channel, isFav -> viewModel.toggleFavorite(channel, isFav) },
                                    onAddProfile = { name, url, avatar -> viewModel.addProfileWithPlaylist(name, url, avatar) },
                                    onSwitchProfile = { profile -> viewModel.switchActiveProfile(profile) },
                                    onDeleteProfile = { id -> viewModel.deleteProfile(id) },
                                    onUpdateProfile = { profile -> viewModel.updateProfile(profile) },
                                    onLoadPlaylist = { url -> viewModel.loadPlaylist(url) },
                                    testResult = testResult,
                                    onTestUrl = { url -> viewModel.testUrl(url) },
                                    onClearTestResult = { viewModel.clearTestResult() },
                                    backupStatus = backupStatus,
                                    onExportBackup = { uri -> viewModel.exportConfiguration(uri) },
                                    onImportBackup = { uri -> viewModel.importConfiguration(uri) },
                                    onClearBackupStatus = { viewModel.clearBackupStatus() }
                                )
                            }
                            is UiState.Error -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.layout.Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        androidx.compose.material3.Text(
                                            text = state.message,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        androidx.compose.material3.Button(
                                            onClick = { viewModel.loadPlaylist("http://190.108.83.69:8000/playlist.m3u") },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                                        ) {
                                            androidx.compose.material3.Text("Cargar Lista por Defecto", color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ensureAudioUnmuted()
        requestGlobalTvAudioFocus()
    }

    override fun onStop() {
        super.onStop()
        abandonGlobalTvAudioFocus()
    }

    private fun ensureAudioUnmuted() {
        val am = audioManager ?: return
        try {
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val isMuted = am.isStreamMute(AudioManager.STREAM_MUSIC)

            if (isMuted || currentVol == 0) {
                val targetVol = (maxVol * 0.8f).toInt().coerceAtLeast(1)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                Log.d(TAG, "Unmuted and set STREAM_MUSIC volume to $targetVol/$maxVol")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/unmuting audio: ${e.message}")
        }
    }

    private fun requestGlobalTvAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                val res = am.requestAudioFocus(request)
                Log.d(TAG, "Requested Global TV AudioFocus result: $res")
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed requesting global TV audio focus: ${e.message}")
        }
    }

    private fun abandonGlobalTvAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(this)
            }
            Log.d(TAG, "Abandoned Global TV AudioFocus")
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus: ${e.message}")
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "MainActivity onAudioFocusChange: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Manejar la pérdida de audio
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Reanudar el audio si es necesario
            }
        }
    }
}
