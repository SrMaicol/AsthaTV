package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SampleM3u
import com.example.data.backup.BackupManager
import com.example.data.room.AppDatabase
import com.example.data.room.FavoriteEntity
import com.example.data.room.UserProfileEntity
import com.example.data.room.WatchHistoryEntity
import com.example.model.M3uChannel
import com.example.parser.M3uParser
import android.net.Uri
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class M3uTestResult(
    val isTesting: Boolean = false,
    val success: Boolean? = null,
    val channelCount: Int = 0,
    val message: String = ""
)

sealed interface UiState {
    object Loading : UiState
    data class Success(val channels: List<M3uChannel>) : UiState
    data class Error(val message: String) : UiState
}

class TvM3uViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.channelDao()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentChannel = MutableStateFlow<M3uChannel?>(null)
    val currentChannel: StateFlow<M3uChannel?> = _currentChannel.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _testResult = MutableStateFlow<M3uTestResult>(M3uTestResult())
    val testResult: StateFlow<M3uTestResult> = _testResult.asStateFlow()

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    val favorites: StateFlow<List<FavoriteEntity>> = dao.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = dao.getWatchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfiles: StateFlow<List<UserProfileEntity>> = dao.getUserProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile: StateFlow<UserProfileEntity?> = userProfiles.map { list ->
        list.find { it.active } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        initializeData()
    }

    private fun initializeData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val existingProfiles = dao.getAllProfilesList()
                if (existingProfiles.isEmpty()) {
                    // Primer inicio: Sembrar perfiles por defecto con avatares de animales optimizados
                    val p1 = UserProfileEntity(
                        profileId = "prof_1",
                        name = "Salón Principal",
                        avatarUrl = "avatar_lion",
                        active = true,
                        m3uUrl = "http://190.108.83.69:8000/playlist.m3u"
                    )
                    val p2 = UserProfileEntity(
                        profileId = "prof_2",
                        name = "Habitación / Deportes",
                        avatarUrl = "avatar_panda",
                        active = false,
                        m3uUrl = ""
                    )
                    dao.insertProfile(p1)
                    dao.insertProfile(p2)
                    loadPlaylist(p1.m3uUrl, navigateToCatalog = false)
                } else {
                    // Migración automática de perfiles antiguos con fotos pesadas a avatares de animales
                    val updatedProfiles = mutableListOf<UserProfileEntity>()
                    existingProfiles.forEach { prof ->
                        if (prof.avatarUrl.contains("unsplash") || prof.avatarUrl.contains("flaticon") || prof.avatarUrl.isBlank()) {
                            val newAvatar = when {
                                prof.avatarUrl.contains("1534528741775") || prof.name.contains("Principal", ignoreCase = true) -> "avatar_lion"
                                prof.avatarUrl.contains("1507003211169") || prof.name.contains("Deporte", ignoreCase = true) -> "avatar_panda"
                                prof.avatarUrl.contains("1535713875002") || prof.name.contains("Cine", ignoreCase = true) -> "avatar_bear"
                                prof.avatarUrl.contains("1494790108377") || prof.name.contains("Música", ignoreCase = true) -> "avatar_wolf"
                                prof.avatarUrl.contains("1570295999919") || prof.name.contains("Noticia", ignoreCase = true) -> "avatar_fox"
                                else -> "avatar_eagle"
                            }
                            updatedProfiles.add(prof.copy(avatarUrl = newAvatar))
                        }
                    }
                    if (updatedProfiles.isNotEmpty()) {
                        dao.insertProfiles(updatedProfiles)
                    }

                    // Cargar el perfil activo usando la lista más reciente disponible
                    val currentList = if (updatedProfiles.isNotEmpty()) dao.getAllProfilesList() else existingProfiles
                    val active = currentList.find { it.active } ?: currentList.firstOrNull()
                    if (active != null) {
                        if (active.m3uUrl.isNotBlank()) {
                            loadPlaylist(active.m3uUrl, navigateToCatalog = false)
                        } else {
                            loadPlaylist("", navigateToCatalog = false)
                        }
                    } else {
                        loadPlaylist("", navigateToCatalog = false)
                    }
                }
            } catch (e: Exception) {
                loadPlaylist("", navigateToCatalog = false)
            }
        }
    }

    fun loadPlaylist(input: String, navigateToCatalog: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _searchQuery.value = ""
            if (navigateToCatalog) {
                _selectedTab.value = 0
            }
            try {
                val clean = input.trim()
                val parsedChannels = if (clean.startsWith("http://", ignoreCase = true) ||
                    clean.startsWith("https://", ignoreCase = true) ||
                    clean.startsWith("//") ||
                    (!clean.contains("\n") && (clean.contains(".m3u", ignoreCase = true) || clean.contains(".m3u8", ignoreCase = true) || clean.contains("/")))
                ) {
                    M3uParser.parseFromUrl(clean)
                } else {
                    M3uParser.parse(clean)
                }

                if (parsedChannels.isNotEmpty()) {
                    _uiState.value = UiState.Success(parsedChannels)
                } else {
                    val sampleChannels = M3uParser.parse(SampleM3u.PLAYLIST)
                    _uiState.value = UiState.Success(sampleChannels)
                }
            } catch (e: Exception) {
                try {
                    val sampleChannels = M3uParser.parse(SampleM3u.PLAYLIST)
                    _uiState.value = UiState.Success(sampleChannels)
                } catch (ex: Exception) {
                    _uiState.value = UiState.Error("Error al cargar la lista M3U: ${e.localizedMessage}")
                }
            }
        }
    }

    fun testUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _testResult.value = M3uTestResult(isTesting = false, success = false, message = "Por favor ingresa una URL válida.")
            return
        }
        viewModelScope.launch {
            _testResult.value = M3uTestResult(isTesting = true)
            try {
                val channels = if (trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) ||
                    trimmed.startsWith("//") ||
                    trimmed.contains(".m3u", ignoreCase = true) || trimmed.contains(".m3u8", ignoreCase = true) || trimmed.contains("/")
                ) {
                    M3uParser.parseFromUrl(trimmed)
                } else {
                    M3uParser.parse(trimmed)
                }

                if (channels.isNotEmpty()) {
                    _testResult.value = M3uTestResult(
                        isTesting = false,
                        success = true,
                        channelCount = channels.size,
                        message = "¡Conexión exitosa! Se encontraron ${channels.size} canales listos para reproducir."
                    )
                } else {
                    _testResult.value = M3uTestResult(
                        isTesting = false,
                        success = false,
                        message = "La URL respondió pero no se detectaron canales M3U válidos."
                    )
                }
            } catch (e: Exception) {
                _testResult.value = M3uTestResult(
                    isTesting = false,
                    success = false,
                    message = "No se pudo conectar con la lista: ${e.localizedMessage ?: "Verifica la URL o servidor"}"
                )
            }
        }
    }

    fun clearTestResult() {
        _testResult.value = M3uTestResult()
    }

    fun switchActiveProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            dao.clearActiveProfiles()
            dao.setProfileActive(profile.profileId)
            loadPlaylist(profile.m3uUrl, navigateToCatalog = true)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            dao.deleteProfile(profileId)
        }
    }

    fun addProfileWithPlaylist(name: String, m3uUrl: String, avatarUrl: String = "avatar_lion") {
        viewModelScope.launch {
            val newProfileId = "prof_${System.currentTimeMillis()}"
            dao.clearActiveProfiles()
            val newProfile = UserProfileEntity(
                profileId = newProfileId,
                name = name,
                avatarUrl = avatarUrl,
                active = true,
                m3uUrl = m3uUrl
            )
            dao.insertProfile(newProfile)
            if (m3uUrl.isNotBlank()) {
                loadPlaylist(m3uUrl, navigateToCatalog = true)
            }
        }
    }

    fun updateProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            dao.insertProfile(profile)
            if (profile.active) {
                loadPlaylist(profile.m3uUrl, navigateToCatalog = false)
            }
        }
    }

    fun selectChannel(channel: M3uChannel) {
        _currentChannel.value = channel
        viewModelScope.launch {
            dao.insertWatchHistory(
                WatchHistoryEntity(
                    id = channel.id,
                    name = channel.name,
                    streamUrl = channel.streamUrl,
                    logoUrl = channel.logoUrl,
                    group = channel.group
                )
            )
        }
    }

    fun clearCurrentChannel() {
        _currentChannel.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun toggleFavorite(channel: M3uChannel, isFavorite: Boolean) {
        viewModelScope.launch {
            if (isFavorite) {
                dao.insertFavorite(
                    FavoriteEntity(
                        id = channel.id,
                        name = channel.name,
                        streamUrl = channel.streamUrl,
                        logoUrl = channel.logoUrl,
                        group = channel.group,
                        epgId = channel.epgId
                    )
                )
            } else {
                dao.removeFavorite(channel.id)
            }
        }
    }



    fun exportConfiguration(uri: Uri) {
        viewModelScope.launch {
            try {
                val profiles = dao.getAllProfilesList()
                val favorites = dao.getAllFavoritesList()
                val result = BackupManager.exportToJson(getApplication(), uri, profiles, favorites)
                if (result.isSuccess) {
                    _backupStatus.value = "Copia exportada con éxito: ${profiles.size} perfiles y ${favorites.size} favoritos guardados."
                } else {
                    _backupStatus.value = "Error al exportar: ${result.exceptionOrNull()?.localizedMessage}"
                }
            } catch (e: Exception) {
                _backupStatus.value = "Error al exportar: ${e.localizedMessage}"
            }
        }
    }

    fun importConfiguration(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = BackupManager.importFromJson(getApplication(), uri)
                if (result.isSuccess) {
                    val payload = result.getOrThrow()
                    if (payload.profiles.isNotEmpty()) {
                        dao.insertProfiles(payload.profiles)
                    }
                    if (payload.favorites.isNotEmpty()) {
                        dao.insertFavorites(payload.favorites)
                    }
                    _backupStatus.value = "Restauración completada: ${payload.profiles.size} perfiles y ${payload.favorites.size} favoritos importados."
                    val active = payload.profiles.find { it.active } ?: payload.profiles.firstOrNull()
                    if (active != null && active.m3uUrl.isNotBlank()) {
                        loadPlaylist(active.m3uUrl, navigateToCatalog = true)
                    }
                } else {
                    _backupStatus.value = "Error al importar: ${result.exceptionOrNull()?.localizedMessage}"
                }
            } catch (e: Exception) {
                _backupStatus.value = "Error al importar: ${e.localizedMessage}"
            }
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = null
    }
}
