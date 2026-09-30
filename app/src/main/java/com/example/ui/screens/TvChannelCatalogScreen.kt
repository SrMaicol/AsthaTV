package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.M3uChannel
import com.example.data.room.FavoriteEntity
import com.example.data.room.WatchHistoryEntity
import com.example.data.room.UserProfileEntity
import com.example.viewmodel.M3uTestResult
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager

// Avatares disponibles para personalización de perfiles (Animales Optimizados 100% Vectoriales, 0 RAM extra)
val AVAILABLE_AVATARS = listOf(
    "avatar_lion" to "León",
    "avatar_panda" to "Panda",
    "avatar_bear" to "Oso",
    "avatar_wolf" to "Lobo",
    "avatar_fox" to "Zorro",
    "avatar_eagle" to "Águila"
)

@Composable
fun ProfileAvatarImage(
    avatarUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color(0xFF3B82F6), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

data class PresetPlaylist(
    val title: String,
    val description: String,
    val url: String,
    val badge: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val RECOMMENDED_PRESETS = listOf(
    PresetPlaylist(
        title = "Servidor IPTV Principal",
        description = "Canales en vivo de alta velocidad (puerto 8000)",
        url = "http://190.108.83.69:8000/playlist.m3u",
        badge = "SERVIDOR EN VIVO",
        icon = Icons.Default.Dns
    ),
    PresetPlaylist(
        title = "Canales Demo HLS & Multi-Audio",
        description = "Streams de prueba HD con Dolby/Stereo integrados",
        url = "",
        badge = "OFFLINE / LOCAL",
        icon = Icons.Default.Movie
    ),
    PresetPlaylist(
        title = "Canales Públicos en Español (IPTV-org)",
        description = "Emisiones de señal abierta de España y Latinoamérica",
        url = "https://iptv-org.github.io/iptv/languages/spa.m3u",
        badge = "LEGAL & ABIERTO",
        icon = Icons.Default.Public
    ),
    PresetPlaylist(
        title = "Noticias Internacionales 24/7",
        description = "Cadenas informativas mundiales en vivo",
        url = "https://iptv-org.github.io/iptv/categories/news.m3u",
        badge = "NOTICIAS",
        icon = Icons.Default.Newspaper
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvChannelCatalogScreen(
    channels: List<M3uChannel>,
    favorites: List<FavoriteEntity>,
    history: List<WatchHistoryEntity>,
    profiles: List<UserProfileEntity>,
    activeProfile: UserProfileEntity?,
    currentChannel: M3uChannel?,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onChannelSelected: (M3uChannel) -> Unit,
    onToggleFavorite: (M3uChannel, Boolean) -> Unit,
    onAddProfile: (String, String, String) -> Unit,
    onSwitchProfile: (UserProfileEntity) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onUpdateProfile: (UserProfileEntity) -> Unit,
    onLoadPlaylist: (String) -> Unit,
    testResult: M3uTestResult = M3uTestResult(),
    onTestUrl: (String) -> Unit = {},
    onClearTestResult: () -> Unit = {},
    backupStatus: String? = null,
    onExportBackup: (Uri) -> Unit = {},
    onImportBackup: (Uri) -> Unit = {},
    onClearBackupStatus: () -> Unit = {}
) {
    val catalogListState = rememberLazyListState()
    var showSearchOverlay by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val categories = remember(channels) {
        val groups = LinkedHashSet<String>(64)
        channels.forEach { groups.add(it.group) }
        groups.toList()
    }

    val favoriteIds = remember(favorites) { favorites.map { it.id }.toSet() }

    // Auto-scroll to currentChannel's category in the catalog when returning from player or when currentChannel changes
    LaunchedEffect(currentChannel?.id, categories) {
        if (currentChannel != null) {
            val groupIndex = categories.indexOf(currentChannel.group)
            if (groupIndex >= 0) {
                try {
                    catalogListState.animateScrollToItem(groupIndex + 1)
                } catch (_: Exception) {}
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                    )
                )
            }
    ) {
        val screenWidth = maxWidth
        val isCompact = screenWidth < 650.dp
        val isMedium = screenWidth in 650.dp..1000.dp

        val contentPadding = when {
            isCompact -> 16.dp
            isMedium -> 24.dp
            else -> 32.dp
        }

        val cardWidth = when {
            isCompact -> 180.dp
            isMedium -> 210.dp
            else -> 230.dp
        }

        val cardHeight = when {
            isCompact -> 120.dp
            isMedium -> 135.dp
            else -> 145.dp
        }

        val searchBarWidthFraction = when {
            isCompact -> 1.0f
            isMedium -> 0.65f
            else -> 0.45f
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(contentPadding)
        ) {
            // Adaptive Header with Active Profile Indicator
            if (isCompact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "App Logo",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "IPTV Stream TV",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        // Active Profile Chip
                        activeProfile?.let { prof ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                modifier = Modifier.clickable { onTabSelected(3) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProfileAvatarImage(
                                        avatarUrl = prof.avatarUrl,
                                        contentDescription = prof.name,
                                        modifier = Modifier.size(20.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = prof.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF38BDF8),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Scrollable Tabs row for compact screens
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val tabs = listOf("Catálogo", "Favoritos", "Recientes", "Perfiles y Listas M3U")
                        tabs.forEachIndexed { index, title ->
                            Button(
                                onClick = { onTabSelected(index) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedTab == index) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                    contentColor = if (selectedTab == index) Color.Black else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(text = title)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "App Logo",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "IPTV Stream TV",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(16.dp))

                        // Active Profile Chip for Tablet / TV
                        activeProfile?.let { prof ->
                            Surface(
                                onClick = { onTabSelected(3) },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProfileAvatarImage(
                                        avatarUrl = prof.avatarUrl,
                                        contentDescription = prof.name,
                                        modifier = Modifier.size(24.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Perfil: ${prof.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }
                    }

                    // Navigation Tabs for Tablet & TV
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val tabs = listOf("Catálogo", "Favoritos", "Recientes", "Perfiles y Listas M3U")
                        tabs.forEachIndexed { index, title ->
                            Button(
                                onClick = { onTabSelected(index) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedTab == index) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                    contentColor = if (selectedTab == index) Color.Black else Color.White
                                )
                            ) {
                                Text(text = title)
                            }
                        }
                    }
                }
            }

            // Main Content Area based on Selected Tab
            when (selectedTab) {
                0 -> { // Catalog by Group
                    val channelsByGroup = remember(channels, searchQuery) {
                        val filtered = if (searchQuery.isBlank()) {
                            channels
                        } else {
                            channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                        filtered.groupBy { it.group }
                    }

                    val groupEntries = remember(channelsByGroup) {
                        channelsByGroup.entries.toList()
                    }

                    LazyColumn(
                        state = catalogListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // Quick Profile Switcher LazyRow at the top of the Catalog
                        if (profiles.isNotEmpty()) {
                            item(key = "catalog_quick_profile_switcher") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Dns, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Cambiar Lista / Perfil Activo:",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        TextButton(
                                            onClick = { onTabSelected(3) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Gestionar listas", color = Color(0xFF38BDF8), fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(profiles, key = { "prof_chip_${it.profileId}" }, contentType = { "profile_chip" }) { prof ->
                                            val isCur = prof.active
                                            Surface(
                                                onClick = { onSwitchProfile(prof) },
                                                shape = RoundedCornerShape(20.dp),
                                                color = Color.Transparent,
                                                border = null,
                                                modifier = Modifier
                                                    .height(42.dp)
                                                    .graphicsLayer {
                                                        clip = true
                                                        shape = RoundedCornerShape(20.dp)
                                                    }
                                                    .drawBehind {
                                                        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())
                                                        val bgColor = if (isCur) Color(0xFF1E3A8A) else Color(0xFF1E293B)
                                                        drawRoundRect(color = bgColor, cornerRadius = cornerRadius)
                                                        val borderColor = if (isCur) Color(0xFF38BDF8) else Color(0xFF334155)
                                                        val strokeWidth = (if (isCur) 2.dp else 1.dp).toPx()
                                                        drawRoundRect(
                                                            color = borderColor,
                                                            cornerRadius = cornerRadius,
                                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                                        )
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    ProfileAvatarImage(
                                                        avatarUrl = prof.avatarUrl,
                                                        contentDescription = prof.name,
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .border(1.dp, if (isCur) Color(0xFF38BDF8) else Color.Gray, CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = prof.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isCur) Color.White else Color(0xFFCBD5E1)
                                                    )
                                                    if (isCur) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .background(Color(0xFF10B981), CircleShape)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        item(key = "quick_add_profile_chip", contentType = "action_chip") {
                                            Surface(
                                                onClick = { onTabSelected(3) },
                                                shape = RoundedCornerShape(20.dp),
                                                color = Color.Transparent,
                                                border = BorderStroke(1.dp, Color(0xFF475569)),
                                                modifier = Modifier.height(42.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("+ Nueva Lista", style = MaterialTheme.typography.bodySmall, color = Color(0xFF38BDF8))
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }

                        item(key = "catalog_search_header", contentType = "search_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        // Isolate search bar layer to avoid triggering full list relayouts
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var isSearchFocused by remember { mutableStateOf(false) }
                                Surface(
                                    onClick = { showSearchOverlay = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSearchFocused) Color(0xFF1E3A8A) else Color(0x1F475569),
                                    border = BorderStroke(
                                        if (isSearchFocused) 2.dp else 1.dp,
                                        if (isSearchFocused) Color(0xFF38BDF8) else Color(0xFF475569)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth(searchBarWidthFraction)
                                        .height(54.dp)
                                        .onFocusChanged { isSearchFocused = it.isFocused }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = if (isSearchFocused) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = if (searchQuery.isNotBlank()) searchQuery else "Buscar canales (Presiona OK para escribir)...",
                                            color = if (searchQuery.isNotBlank()) Color.White else Color(0xFF94A3B8),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Text(
                                    text = "${channels.size} canales disponibles",
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        items(
                            items = groupEntries,
                            key = { "group_${it.key}" },
                            contentType = { "channel_group" }
                        ) { entry ->
                            val group = entry.key
                            val groupChannels = entry.value
                            if (groupChannels.isNotEmpty()) {
                                val rowState = remember(group) { androidx.compose.foundation.lazy.LazyListState() }

                                LaunchedEffect(currentChannel?.id, group) {
                                    if (currentChannel != null && currentChannel.group == group) {
                                        val indexInGroup = groupChannels.indexOfFirst { it.id == currentChannel.id }
                                        if (indexInGroup >= 0) {
                                            try {
                                                rowState.scrollToItem(indexInGroup)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = group,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color(0xFF94A3B8),
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        Text(
                                            text = "${groupChannels.size} canales",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF64748B),
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                    }
                                    LazyRow(
                                        state = rowState,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(groupChannels, key = { it.id }, contentType = { "channel_card" }) { channel ->
                                            val isFav = favoriteIds.contains(channel.id)
                                            val isCurrent = currentChannel?.id == channel.id
                                            ChannelCard(
                                                channel = channel,
                                                isFavorite = isFav,
                                                isCurrent = isCurrent,
                                                cardWidth = cardWidth,
                                                cardHeight = cardHeight,
                                                onClick = { onChannelSelected(channel) },
                                                onFavoriteToggle = { onToggleFavorite(channel, !isFav) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> { // Favorites
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Canales Favoritos (${favorites.size})",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        if (favorites.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No hay canales favoritos guardados.", color = Color.Gray)
                            }
                        } else {
                            val favRowState = rememberLazyListState()
                            LaunchedEffect(currentChannel?.id) {
                                if (currentChannel != null) {
                                    val index = favorites.indexOfFirst { it.id == currentChannel.id }
                                    if (index >= 0) {
                                        try { favRowState.animateScrollToItem(index) } catch (_: Exception) {}
                                    }
                                }
                            }
                            LazyRow(
                                state = favRowState,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(favorites, key = { it.id }, contentType = { "channel_card" }) { fav ->
                                    val channel = M3uChannel(fav.id, fav.name, fav.streamUrl, fav.logoUrl, fav.group, fav.epgId)
                                    val isCurrent = currentChannel?.id == channel.id
                                    ChannelCard(
                                        channel = channel,
                                        isFavorite = true,
                                        isCurrent = isCurrent,
                                        cardWidth = cardWidth,
                                        cardHeight = cardHeight,
                                        onClick = { onChannelSelected(channel) },
                                        onFavoriteToggle = { onToggleFavorite(channel, false) }
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> { // Recent History
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Reproducidos Recientemente",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        if (history.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Aún no hay historial de reproducción.", color = Color.Gray)
                            }
                        } else {
                            val histRowState = rememberLazyListState()
                            LaunchedEffect(currentChannel?.id) {
                                if (currentChannel != null) {
                                    val index = history.indexOfFirst { it.id == currentChannel.id }
                                    if (index >= 0) {
                                        try { histRowState.animateScrollToItem(index) } catch (_: Exception) {}
                                    }
                                }
                            }
                            LazyRow(
                                state = histRowState,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(history, key = { it.id }, contentType = { "channel_card" }) { hist ->
                                    val channel = M3uChannel(hist.id, hist.name, hist.streamUrl, hist.logoUrl, hist.group, "")
                                    val isFav = favoriteIds.contains(channel.id)
                                    val isCurrent = currentChannel?.id == channel.id
                                    ChannelCard(
                                        channel = channel,
                                        isFavorite = isFav,
                                        isCurrent = isCurrent,
                                        cardWidth = cardWidth,
                                        cardHeight = cardHeight,
                                        onClick = { onChannelSelected(channel) },
                                        onFavoriteToggle = { onToggleFavorite(channel, !isFav) }
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> { // HIGHLY OPTIMIZED PROFILES & M3U PLAYLISTS MANAGER
                    var subTab by remember { mutableStateOf(0) } // 0: Mis Perfiles, 1: Añadir Lista/URL, 2: Listas Recomendadas, 3: Respaldo
                    var newProfileName by remember { mutableStateOf("") }
                    var newM3uUrl by remember { mutableStateOf("") }
                    var selectedAvatarIndex by remember { mutableStateOf(0) }
                    var rawM3uText by remember { mutableStateOf("") }
                    var showPasteDialog by remember { mutableStateOf(false) }
                    var editingProfile by remember { mutableStateOf<UserProfileEntity?>(null) }
                    var profileToDelete by remember { mutableStateOf<UserProfileEntity?>(null) }

                    val exportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json")
                    ) { uri: Uri? ->
                        if (uri != null) {
                            onExportBackup(uri)
                        }
                    }

                    val importLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            onImportBackup(uri)
                        }
                    }

                    // Dialog to edit existing profile
                    editingProfile?.let { profile ->
                        var editName by remember(profile) { mutableStateOf(profile.name) }
                        var editUrl by remember(profile) { mutableStateOf(profile.m3uUrl) }
                        var editAvatar by remember(profile) { mutableStateOf(profile.avatarUrl) }

                        AlertDialog(
                            onDismissRequest = { editingProfile = null },
                            title = { Text("Editar Perfil / Lista M3U", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = editName,
                                        onValueChange = { editName = it },
                                        label = { Text("Nombre del Perfil") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = editUrl,
                                        onValueChange = { editUrl = it },
                                        label = { Text("URL de Lista M3U") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("Cambiar Avatar:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        AVAILABLE_AVATARS.forEach { (avatarKey, label) ->
                                            val isSel = editAvatar == avatarKey
                                            ProfileAvatarImage(
                                                avatarUrl = avatarKey,
                                                contentDescription = label,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .border(if (isSel) 3.dp else 1.dp, if (isSel) Color(0xFF38BDF8) else Color.Gray, CircleShape)
                                                    .clickable { editAvatar = avatarKey }
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        onUpdateProfile(profile.copy(name = editName.trim(), m3uUrl = editUrl.trim(), avatarUrl = editAvatar))
                                        editingProfile = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                                ) {
                                    Text("Guardar Cambios", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { editingProfile = null }) {
                                    Text("Cancelar", color = Color.Gray)
                                }
                            },
                            containerColor = Color(0xFF1E293B)
                        )
                    }

                    // Dialog to paste raw M3U text
                    if (showPasteDialog) {
                        AlertDialog(
                            onDismissRequest = { showPasteDialog = false },
                            title = { Text("Pegar Contenido M3U Completo", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Pega el texto que empieza con #EXTM3U:", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                                    OutlinedTextField(
                                        value = rawM3uText,
                                        onValueChange = { rawM3uText = it },
                                        modifier = Modifier.fillMaxWidth().height(160.dp),
                                        placeholder = { Text("#EXTM3U\n#EXTINF:-1,Canal 1\nhttp://...", color = Color.DarkGray) }
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val trimmedM3u = rawM3uText.trim()
                                        if (trimmedM3u.isNotBlank()) {
                                            onLoadPlaylist(trimmedM3u)
                                            showPasteDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                                ) {
                                    Text("Cargar Lista", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPasteDialog = false }) {
                                    Text("Cancelar", color = Color.Gray)
                                }
                            },
                            containerColor = Color(0xFF1E293B)
                        )
                    }

                    // Dialog to delete profile with full confirmation and warning
                    profileToDelete?.let { prof ->
                        AlertDialog(
                            onDismissRequest = { profileToDelete = null },
                            icon = {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = {
                                Text(
                                    "¿Eliminar perfil \"${prof.name}\"?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Esta acción no se puede deshacer. Se eliminará la configuración de la lista M3U asociada a este perfil:",
                                        color = Color(0xFFCBD5E1),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (prof.m3uUrl.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF0F172A),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = prof.m3uUrl,
                                                color = Color(0xFF94A3B8),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                    if (prof.active) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFB45309).copy(alpha = 0.25f),
                                            border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Aviso: Este perfil está actualmente activo. Al eliminarlo, se activará otro perfil disponible automáticamente.",
                                                    color = Color(0xFFFCD34D),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        onDeleteProfile(prof.profileId)
                                        profileToDelete = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sí, Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = { profileToDelete = null },
                                    border = BorderStroke(1.dp, Color(0xFF64748B))
                                ) {
                                    Text("Cancelar", color = Color(0xFFCBD5E1))
                                }
                            },
                            containerColor = Color(0xFF1E293B)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 1. HERO BANNER: Active Profile Summary
                        activeProfile?.let { active ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(2.dp, Color(0xFF38BDF8))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box {
                                            ProfileAvatarImage(
                                                avatarUrl = active.avatarUrl,
                                                contentDescription = active.name,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .border(2.dp, Color(0xFF38BDF8), CircleShape)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(Color(0xFF10B981), CircleShape)
                                                    .border(2.dp, Color(0xFF1E293B), CircleShape)
                                                    .align(Alignment.BottomEnd)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = active.name,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                                    border = BorderStroke(1.dp, Color(0xFF10B981))
                                                ) {
                                                    Text(
                                                        text = "ACTIVO",
                                                        color = Color(0xFF10B981),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = "${channels.size} canales cargados actualmente",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF38BDF8)
                                            )

                                            if (active.m3uUrl.isNotBlank()) {
                                                Text(
                                                    text = active.m3uUrl,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF94A3B8),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                if (active.m3uUrl.isNotBlank()) {
                                                    onLoadPlaylist(active.m3uUrl)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Recargar", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { onTabSelected(0) },
                                            border = BorderStroke(1.dp, Color(0xFF38BDF8))
                                        ) {
                                            Text("Ver Catálogo", color = Color(0xFF38BDF8))
                                        }
                                    }
                                }
                            }
                        }

                        // Banner de Notificación de Respaldo / Restauración
                        AnimatedVisibility(visible = backupStatus != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0369A1),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF38BDF8))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = backupStatus ?: "",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    IconButton(onClick = onClearBackupStatus) {
                                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                                    }
                                }
                            }
                        }

                        // 2. Sub-Tabs Segments
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val subTabs = listOf(
                                Triple(0, "Mis Perfiles (${profiles.size})", Icons.Default.People),
                                Triple(1, "Añadir Lista", Icons.Default.AddLink),
                                Triple(2, "Sugeridas", Icons.Default.AutoAwesome),
                                Triple(3, "Respaldo (.json)", Icons.Default.CloudSync)
                            )
                            subTabs.forEach { (idx, title, icon) ->
                                val isSel = subTab == idx
                                Surface(
                                    onClick = { subTab = idx },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) Color(0xFF1E3A8A) else Color.Transparent,
                                    border = if (isSel) BorderStroke(1.dp, Color(0xFF38BDF8)) else null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, contentDescription = null, tint = if (isSel) Color(0xFF38BDF8) else Color.Gray, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color.White else Color(0xFF94A3B8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Sub-Tab Content
                        when (subTab) {
                            0 -> { // SUB-TAB 0: Mis Perfiles
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Perfiles Configurados en el Dispositivo",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                                    exportLauncher.launch("iptv_backup_$dateStr.json")
                                                },
                                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Exportar", color = Color(0xFF38BDF8), fontSize = 12.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                                },
                                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Importar", color = Color(0xFF38BDF8), fontSize = 12.sp)
                                            }

                                            Button(
                                                onClick = { subTab = 1 },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Nuevo", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(profiles, key = { it.profileId }, contentType = { "profile_card" }) { profile ->
                                            EnhancedProfileCard(
                                                profile = profile,
                                                isActive = profile.active,
                                                onActivate = { onSwitchProfile(profile) },
                                                onReload = { if (profile.m3uUrl.isNotBlank()) onLoadPlaylist(profile.m3uUrl) },
                                                onEdit = { editingProfile = profile },
                                                onDelete = { profileToDelete = profile }
                                            )
                                        }

                                        item(key = "add_profile_card_in_row", contentType = "add_profile") {
                                            AddProfileCard(onClick = { subTab = 1 })
                                        }
                                    }
                                }
                            }

                            1 -> { // SUB-TAB 1: Añadir Lista / URL con Test de Conexión
                                Card(
                                    modifier = Modifier.fillMaxWidth(if (isCompact) 1f else 0.88f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFF38BDF8))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Configurar Nueva Lista M3U",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            TextButton(onClick = { showPasteDialog = true }) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Pegar Texto M3U", color = Color(0xFF38BDF8))
                                            }
                                        }

                                        OutlinedTextField(
                                            value = newProfileName,
                                            onValueChange = { newProfileName = it },
                                            label = { Text("Nombre del Perfil o Lista", color = Color.Gray) },
                                            placeholder = { Text("ej. Deportes HD, Cine Premium, Sala...", color = Color.DarkGray) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF38BDF8),
                                                unfocusedBorderColor = Color(0xFF475569),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = newM3uUrl,
                                                onValueChange = {
                                                    newM3uUrl = it
                                                    onClearTestResult()
                                                },
                                                label = { Text("URL de Lista M3U / M3U8", color = Color.Gray) },
                                                placeholder = { Text("http://servidor:puerto/playlist.m3u", color = Color.DarkGray) },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF38BDF8),
                                                    unfocusedBorderColor = Color(0xFF475569),
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )

                                            // Connection Test Button and Results
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = { onTestUrl(newM3uUrl) },
                                                    enabled = newM3uUrl.isNotBlank() && !testResult.isTesting,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF0284C7)
                                                    )
                                                ) {
                                                    if (testResult.isTesting) {
                                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Probando conexión...")
                                                    } else {
                                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(24.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Probar Conexión (Ping)")
                                                    }
                                                }

                                                if (testResult.success != null) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (testResult.success == true) Color(0xFF065F46) else Color(0xFF7F1D1D)
                                                    ) {
                                                        Text(
                                                            text = testResult.message,
                                                            color = Color.White,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Avatar Selector
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Icono / Avatar del Perfil:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.horizontalScroll(rememberScrollState())
                                            ) {
                                                AVAILABLE_AVATARS.forEachIndexed { index, (avatarKey, label) ->
                                                    val isSelected = selectedAvatarIndex == index
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable { selectedAvatarIndex = index }
                                                            .background(if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color.Transparent)
                                                            .padding(6.dp)
                                                    ) {
                                                        ProfileAvatarImage(
                                                            avatarUrl = avatarKey,
                                                            contentDescription = label,
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .clip(CircleShape)
                                                                .border(
                                                                    if (isSelected) 3.dp else 1.dp,
                                                                    if (isSelected) Color(0xFF38BDF8) else Color.Gray,
                                                                    CircleShape
                                                                )
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isSelected) Color(0xFF38BDF8) else Color.Gray
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    val trimmedName = newProfileName.trim()
                                                    val trimmedUrl = newM3uUrl.trim()
                                                    if (trimmedName.isNotBlank() && trimmedUrl.isNotBlank()) {
                                                        val avatarUrl = AVAILABLE_AVATARS.getOrNull(selectedAvatarIndex)?.first
                                                            ?: AVAILABLE_AVATARS.first().first
                                                        onAddProfile(trimmedName, trimmedUrl, avatarUrl)
                                                        newProfileName = ""
                                                        newM3uUrl = ""
                                                        onClearTestResult()
                                                        subTab = 0
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                                enabled = newProfileName.isNotBlank() && newM3uUrl.isNotBlank()
                                            ) {
                                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Guardar Perfil y Cargar Canales", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> { // SUB-TAB 2: Listas Recomendadas y Servidores IPTV
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(
                                        text = "Listas Públicas, Servidores y Pruebas Verificadas",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Haz clic en cualquiera de estas listas para probar la reproducción o guardarla como perfil:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8)
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        RECOMMENDED_PRESETS.forEach { preset ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                                border = BorderStroke(1.dp, Color(0xFF334155))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(44.dp)
                                                                .background(Color(0xFF0F172A), RoundedCornerShape(10.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(preset.icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                                                        }
                                                        Spacer(modifier = Modifier.width(14.dp))
                                                        Column {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = preset.title,
                                                                    style = MaterialTheme.typography.titleMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color.White
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = Color(0xFF38BDF8).copy(alpha = 0.2f)
                                                                ) {
                                                                    Text(
                                                                        text = preset.badge,
                                                                        color = Color(0xFF38BDF8),
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                            Text(
                                                                text = preset.description,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = Color(0xFF94A3B8)
                                                            )
                                                            if (preset.url.isNotBlank()) {
                                                                Text(
                                                                    text = preset.url,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = Color(0xFF64748B),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Button(
                                                            onClick = { onLoadPlaylist(preset.url) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                                                        ) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Cargar Lista", color = Color.Black, fontWeight = FontWeight.Bold)
                                                        }

                                                        OutlinedButton(
                                                            onClick = {
                                                                onAddProfile(preset.title, preset.url, AVAILABLE_AVATARS.first().first)
                                                                subTab = 0
                                                            },
                                                            border = BorderStroke(1.dp, Color(0xFF38BDF8))
                                                        ) {
                                                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Guardar", color = Color(0xFF38BDF8))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            3 -> { // SUB-TAB 3: Respaldo y Restauración de Configuración Local (.json)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                    modifier = Modifier.fillMaxWidth(if (isCompact) 1f else 0.85f)
                                ) {
                                    Text(
                                        text = "Copia de Seguridad y Restauración Local",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Guarda o recupera todos tus perfiles, listas M3U y canales favoritos mediante un archivo JSON local sin necesidad de cuentas ni servidores externos.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF94A3B8)
                                    )

                                    // Card Export
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                        border = BorderStroke(1.dp, Color(0xFF334155))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = "Exportar Respaldo (.json)",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "Genera una copia con tus ${profiles.size} perfiles y ${favorites.size} canales favoritos.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "Podrás guardar el archivo en la memoria interna de tu dispositivo, memoria USB o subirlo a la nube (Google Drive).",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF64748B)
                                            )

                                            Button(
                                                onClick = {
                                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                                    exportLauncher.launch("iptv_backup_$dateStr.json")
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Exportar Copia de Seguridad", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Card Import
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                        border = BorderStroke(1.dp, Color(0xFF334155))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = "Restaurar Respaldo (.json)",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "Restaura perfiles, listas M3U y favoritos desde un archivo previo.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "Selecciona un archivo JSON creado anteriormente. La app restaurará los perfiles y cargará automáticamente la lista activa.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF64748B)
                                            )

                                            Button(
                                                onClick = {
                                                    importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.White)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Seleccionar y Restaurar Archivo", color = Color.White, fontWeight = FontWeight.Bold)
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
    }

    if (showSearchOverlay) {
        BackHandler {
            keyboardController?.hide()
            focusManager.clearFocus()
            showSearchOverlay = false
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20F172A)) // 95% opacity dark background
                .padding(32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Búsqueda de Canales",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    var isCloseFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            showSearchOverlay = false
                        },
                        modifier = Modifier
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .background(if (isCloseFocused) Color(0xFFEF4444) else Color(0x1AFFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                val textRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    delay(250)
                    try {
                        textRequester.requestFocus()
                    } catch (_: Exception) {}
                }
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Escribe el nombre del canal...", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(textRequester),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                val filteredChannels = remember(channels, searchQuery) {
                    if (searchQuery.isBlank()) {
                        emptyList()
                    } else {
                        channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }
                }
                
                if (filteredChannels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "Escribe arriba para buscar canales..." else "No se encontraron canales con ese nombre.",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    Text(
                        text = "Resultados (${filteredChannels.size} encontrados):",
                        color = Color(0xFF38BDF8),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(240.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredChannels) { channel ->
                            ChannelCard(
                                channel = channel,
                                isFavorite = favoriteIds.contains(channel.id),
                                isCurrent = (currentChannel?.id == channel.id),
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onChannelSelected(channel)
                                    showSearchOverlay = false
                                },
                                onFavoriteToggle = {
                                    onToggleFavorite(channel, !favoriteIds.contains(channel.id))
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
fun AddProfileCard(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isFocused || isHovered

    val animatedScale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.05f else 1.0f,
        label = "addProfileScale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .width(260.dp)
            .height(230.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .hoverable(interactionSource = interactionSource)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                clip = true
                shape = RoundedCornerShape(16.dp)
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(
            if (isHighlighted) 2.dp else 1.dp,
            if (isHighlighted) Color(0xFF38BDF8) else Color(0xFF334155)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(Color(0xFF1E293B), CircleShape)
                    .border(2.dp, Color(0xFF38BDF8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Nueva Lista / Perfil",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Añadir URL M3U personalizada",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun EnhancedProfileCard(
    profile: UserProfileEntity,
    isActive: Boolean,
    onActivate: () -> Unit,
    onReload: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isFocused || isHovered

    val animatedScale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.05f else 1.0f,
        label = "profileScale"
    )

    Card(
        onClick = {
            if (!isActive) onActivate() else onReload()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = null,
        modifier = Modifier
            .width(260.dp)
            .height(230.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .hoverable(interactionSource = interactionSource)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                clip = true
                shape = RoundedCornerShape(16.dp)
            }
            .drawBehind {
                val cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                val bgColor = if (isActive) Color(0xFF1E3A8A) else Color(0xFF1E293B)
                drawRoundRect(
                    color = bgColor,
                    cornerRadius = cornerRadius
                )
                val borderColor = when {
                    isHighlighted -> Color(0xFF38BDF8)
                    isActive -> Color(0xFF38BDF8)
                    else -> Color(0x33FFFFFF)
                }
                val strokeWidth = if (isHighlighted) 3.dp.toPx() else if (isActive) 2.dp.toPx() else 1.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = cornerRadius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box {
                        ProfileAvatarImage(
                            avatarUrl = profile.avatarUrl,
                            contentDescription = profile.name,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(2.dp, if (isActive) Color(0xFF38BDF8) else Color.Gray, CircleShape)
                        )
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                                    .border(2.dp, Color(0xFF1E3A8A), CircleShape)
                                    .align(Alignment.BottomEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Activo",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Text(
                                    text = "EN USO",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // URL Status
            Text(
                text = if (profile.m3uUrl.isNotBlank()) "URL: ${profile.m3uUrl}" else "Sin lista configurada",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Bottom Activation / Switch Trigger Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isActive) {
                    Button(
                        onClick = onActivate,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Activar y Cargar Lista", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onReload,
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lista Activa (Recargar)", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(
    channel: M3uChannel,
    isFavorite: Boolean,
    isCurrent: Boolean,
    cardWidth: Dp = 220.dp,
    cardHeight: Dp = 140.dp,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }

    val logoRequest = remember(channel.logoUrl) {
        if (channel.logoUrl.isNotBlank()) {
            ImageRequest.Builder(context)
                .data(channel.logoUrl)
                .size(100, 100)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .crossfade(false)
                .build()
        } else null
    }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = when {
            isFocused -> Color(0xFF1E3A8A)
            isCurrent -> Color(0xFF0F2B5C)
            else -> Color(0xFF1E293B)
        },
        border = BorderStroke(
            if (isFocused || isCurrent) 2.5.dp else 1.dp,
            if (isFocused || isCurrent) Color(0xFF38BDF8) else Color(0x22FFFFFF)
        ),
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = true
                shape = RoundedCornerShape(14.dp)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (logoRequest != null) {
                        AsyncImage(
                            model = logoRequest,
                            contentDescription = channel.name,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = if (isFocused) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFocused) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF38BDF8)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "VER",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        IconButton(
                            onClick = onFavoriteToggle,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (isFavorite) Color.Red else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isFocused || isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isFocused -> Color.White
                            isCurrent -> Color(0xFF38BDF8)
                            else -> Color(0xFFE2E8F0)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCurrent) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF38BDF8))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "EN VIVO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
