package com.ciphertun.aetherwave.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ciphertun.aetherwave.data.LibraryStore
import com.ciphertun.aetherwave.download.Downloader
import com.ciphertun.aetherwave.model.DownloadStatus
import com.ciphertun.aetherwave.model.FEATURED_COUNTRIES
import com.ciphertun.aetherwave.model.FEATURED_MUSIC_COUNTRIES
import com.ciphertun.aetherwave.model.PodcastEpisode
import com.ciphertun.aetherwave.model.Playlist
import com.ciphertun.aetherwave.model.PodcastShow
import com.ciphertun.aetherwave.model.Track
import com.ciphertun.aetherwave.playback.PlayerManager
import com.ciphertun.aetherwave.ui.theme.NeonCyan
import com.ciphertun.aetherwave.ui.theme.NeonPurple
import com.ciphertun.aetherwave.ui.theme.SurfaceElevated
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

private enum class Tab { MUSIC, DISCOVER, PODCASTS, LIBRARY }

@Composable
fun HomeScreen(
    viewModel: SearchViewModel = viewModel(),
    onImportAudio: () -> Unit = {}
) {
    var tab by remember { mutableStateOf(Tab.MUSIC) }
    var playerExpanded by remember { mutableStateOf(false) }
    var queueSheetOpen by remember { mutableStateOf(false) }
    var settingsSheetOpen by remember { mutableStateOf(false) }
    var lyricsSheetOpen by remember { mutableStateOf(false) }
    var lyricsText by remember { mutableStateOf<String?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    val repository = remember { com.ciphertun.aetherwave.data.MusicRepository() }
    val musicState by viewModel.music.collectAsState()
    val podcastState by viewModel.podcasts.collectAsState()
    val nowPlaying by PlayerManager.nowPlaying.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()
    val queue by PlayerManager.queue.collectAsState()
    val currentIndex by PlayerManager.currentIndex.collectAsState()
    val shuffleEnabled by PlayerManager.shuffleEnabled.collectAsState()
    val repeatMode by PlayerManager.repeatMode.collectAsState()
    val playbackSpeed by PlayerManager.playbackSpeed.collectAsState()
    val sleepTimerEndsAt by PlayerManager.sleepTimerEndsAtMillis.collectAsState()
    val librarySnapshot by LibraryStore.snapshot.collectAsState()
    val scope = rememberCoroutineScope()

    // Polls DownloadManager for real progress/completion regardless of which
    // tab is showing, so a download finished in the background (or while the
    // user was on a different tab) still shows up promptly everywhere —
    // the "already downloaded" badge on search rows included.
    LaunchedEffect(librarySnapshot.entries.any { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RUNNING }) {
        while (librarySnapshot.entries.any { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RUNNING }) {
            delay(1200)
            LibraryStore.reconcile()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    if (nowPlaying != null) {
                        MiniPlayerBar(
                            track = nowPlaying!!,
                            isPlaying = isPlaying,
                            onExpand = { playerExpanded = true },
                            onQueue = { queueSheetOpen = true }
                        )
                    }
                    NavigationBar(containerColor = SurfaceElevated) {
                        NavigationBarItem(
                            selected = tab == Tab.MUSIC,
                            onClick = { tab = Tab.MUSIC },
                            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Music") },
                            label = { Text("Music") }
                        )
                        NavigationBarItem(
                            selected = tab == Tab.DISCOVER,
                            onClick = { tab = Tab.DISCOVER },
                            icon = { Icon(Icons.Filled.Explore, contentDescription = "Discover") },
                            label = { Text("Discover") }
                        )
                        NavigationBarItem(
                            selected = tab == Tab.PODCASTS,
                            onClick = { tab = Tab.PODCASTS },
                            icon = { Icon(Icons.Filled.Podcasts, contentDescription = "Podcasts") },
                            label = { Text("Podcasts") }
                        )
                        NavigationBarItem(
                            selected = tab == Tab.LIBRARY,
                            onClick = { tab = Tab.LIBRARY },
                            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Library") },
                            label = { Text("Library") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tab.MUSIC -> MusicTab(
                        state = musicState,
                        onSearch = viewModel::searchMusic,
                        onSetCountry = viewModel::setMusicCountry,
                        onSettings = { settingsSheetOpen = true }
                    )
                    Tab.DISCOVER -> DiscoverTab(
                        state = musicState,
                        onSearch = viewModel::searchMusic
                    )
                    Tab.PODCASTS -> PodcastTab(
                        state = podcastState,
                        onSearch = viewModel::searchPodcasts,
                        onOpenShow = viewModel::openPodcast,
                        onBack = viewModel::closePodcast,
                        onSetCountry = viewModel::setCountry
                    )
                    Tab.LIBRARY -> LibraryTab(onImportAudio = onImportAudio)
                }
            }
        }

        if (queueSheetOpen) {
            ModalBottomSheet(onDismissRequest = { queueSheetOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text(
                        "Up next",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    if (queue.isEmpty()) {
                        Text("Your queue is empty", modifier = Modifier.padding(20.dp))
                    } else {
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            itemsIndexed(queue, key = { _, t -> t.id }) { index, track ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { PlayerManager.playQueueIndex(index) }.padding(horizontal = 16.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = track.artworkUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceElevated)
                                    )
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (index == currentIndex) {
                                        Icon(Icons.Filled.Equalizer, "Playing", tint = NeonCyan)
                                    }
                                    IconButton(onClick = { PlayerManager.removeFromQueue(index) }) {
                                        Icon(Icons.Filled.Close, "Remove from queue")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (settingsSheetOpen) {
            ModalBottomSheet(onDismissRequest = { settingsSheetOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Aetherwave", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Music, podcasts and offline listening", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudDone, null, tint = NeonCyan)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Local music service")
                            Text("Connected on 127.0.0.1:17843", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { settingsSheetOpen = false; onImportAudio() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FileOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import music from this device")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Version 0.3.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        if (lyricsSheetOpen && nowPlaying != null) {
            ModalBottomSheet(onDismissRequest = { lyricsSheetOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Lyrics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${nowPlaying!!.title} · ${nowPlaying!!.artist}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    when {
                        lyricsLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = NeonCyan)
                        lyricsText.isNullOrBlank() -> Text("Lyrics aren't available for this track.")
                        else -> LazyColumn(Modifier.heightIn(max = 520.dp)) {
                            item { Text(lyricsText!!, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = playerExpanded && nowPlaying != null,
            enter = slideInVertically(animationSpec = tween(320)) { it },
            exit = slideOutVertically(animationSpec = tween(280)) { it }
        ) {
            nowPlaying?.let { track ->
                val context = LocalContext.current
                val downloader = remember { Downloader(context) }
                val isFavorite = track.id in librarySnapshot.favoriteIds
                val hasMultiple = queue.size > 1
                NowPlayingScreen(
                    track = track,
                    isPlaying = isPlaying,
                    isFavorite = isFavorite,
                    isDownloadable = track.isDownloadable,
                    hasNext = hasMultiple,
                    hasPrevious = hasMultiple,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    playbackSpeed = playbackSpeed,
                    sleepTimerEndsAtMillis = sleepTimerEndsAt,
                    onToggleFavorite = { scope.launch { LibraryStore.toggleFavorite(track) } },
                    onDownload = { scope.launch { downloader.downloadTrack(track) } },
                    onCollapse = { playerExpanded = false },
                    onSkipNext = { PlayerManager.skipNext() },
                    onSkipPrevious = { PlayerManager.skipPrevious() },
                    onToggleShuffle = { PlayerManager.toggleShuffle() },
                    onCycleRepeat = { PlayerManager.cycleRepeatMode() },
                    onSetSpeed = { PlayerManager.setPlaybackSpeed(it) },
                    onScheduleSleepTimer = { PlayerManager.scheduleSleepTimer(it) },
                    onCancelSleepTimer = { PlayerManager.cancelSleepTimer() },
                    onOpenLyrics = {
                        lyricsSheetOpen = true
                        lyricsLoading = true
                        lyricsText = null
                        scope.launch {
                            lyricsText = repository.lyricsFor(track)
                            lyricsLoading = false
                        }
                    },
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}\n${track.streamUrl}")
                        }
                        context.startActivity(Intent.createChooser(send, "Share track"))
                    },
                    onOpenQueue = { queueSheetOpen = true }
                )
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun MusicTab(
    state: MusicUiState,
    onSearch: (String) -> Unit,
    onSetCountry: (String) -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val downloader = remember { Downloader(context) }
    val scope = rememberCoroutineScope()
    val recentlyPlayedSession by PlayerManager.recentlyPlayed.collectAsState()
    val librarySnapshot by LibraryStore.snapshot.collectAsState()
    val recentlyPlayed = (recentlyPlayedSession + librarySnapshot.recentlyPlayed).distinctBy { it.id }.take(15)
    val isBrowsingHome = state.query.isBlank()

    Column(Modifier.fillMaxSize()) {
        MusicHeader(onSettings = onSettings)
        SearchField(
            value = state.query,
            placeholder = "Search songs, artists, albums…",
            onSearch = onSearch
        )
        if (state.query.isBlank()) {
            val snapshot by LibraryStore.snapshot.collectAsState()
            if (snapshot.searchHistory.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snapshot.searchHistory, key = { it }) { query ->
                        AssistChip(onClick = { onSearch(query) }, label = { Text(query, maxLines = 1) }, leadingIcon = { Icon(Icons.Filled.History, null, modifier = Modifier.size(16.dp)) })
                    }
                }
            }
        }
        DiscoveryChips(onSearch = onSearch)
        MoodChips(onSearch = onSearch)
        SearchHistoryRow(onSearch = onSearch)
        when {
            state.loadState == LoadState.LOADING && isBrowsingHome && state.trending.isEmpty() -> ShimmerList()
            !isBrowsingHome && state.loadState == LoadState.LOADING -> ShimmerList()
            !isBrowsingHome && state.tracks.isEmpty() -> EmptyState("No tracks found — try another search")
            !isBrowsingHome -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onPlay = { PlayerManager.playQueue(state.tracks, index) },
                        onDownload = if (track.isDownloadable) {
                            { scope.launch { downloader.downloadTrack(track) } }
                        } else null,
                        radioTracks = state.tracks
                    )
                }
            }
            else -> {
                val countryName = FEATURED_MUSIC_COUNTRIES.firstOrNull { it.first == state.musicCountry }?.second
                    ?: state.musicCountry
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(greeting(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Openly licensed music from around the world",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    val discoveryPool = (recentlyPlayed + state.trending + state.newReleases + state.countryTracks).distinctBy { it.id }
                    if (discoveryPool.isNotEmpty()) {
                        item {
                            FeaturedMixCard(
                                tracks = discoveryPool,
                                onPlay = { PlayerManager.playQueue(discoveryPool.shuffled(), 0) }
                            )
                        }
                        item { MusicRail(title = "Quick Mix", tracks = discoveryPool.shuffled().take(12)) }
                    }
                    if (recentlyPlayed.isNotEmpty()) {
                        item { MusicRail(title = "Recently played", tracks = recentlyPlayed) }
                    }
                    if (state.newReleases.isNotEmpty()) {
                        item { MusicRail(title = "New releases on Jamendo", tracks = state.newReleases) }
                    }
                    val sourceTracks = state.trending.groupBy { it.source }.values.flatten().distinctBy { it.id }
                    if (sourceTracks.isNotEmpty()) {
                        item { MusicRail(title = "Open & independent", tracks = sourceTracks) }
                    }
                    val artistPicks = discoveryPool.groupBy { it.artist }.values.mapNotNull { it.firstOrNull() }.take(12)
                    if (artistPicks.isNotEmpty()) {
                        item { ArtistRail(title = "Artists to explore", tracks = artistPicks, onSearch = onSearch) }
                    }
                    item {
                        Text(
                            "Artists based in",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        CountryChipRow(
                            options = FEATURED_MUSIC_COUNTRIES,
                            selected = state.musicCountry,
                            onSelect = onSetCountry
                        )
                    }
                    if (state.countryTracks.isNotEmpty()) {
                        item { MusicRail(title = countryName, tracks = state.countryTracks) }
                    } else {
                        item {
                            Text(
                                "No Jamendo artists have tagged $countryName as home yet — try another country, or search by name/genre above",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    if (state.trending.isNotEmpty()) {
                        item { MusicRail(title = "Popular this month", tracks = state.trending) }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}


@Composable
private fun MusicHeader(onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "AETHERWAVE",
                style = MaterialTheme.typography.labelLarge,
                color = NeonCyan,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                greeting(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Surface(
            shape = CircleShape,
            color = NeonPurple.copy(alpha = 0.18f)
        ) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = NeonCyan)
            }
        }
    }
}

@Composable
private fun DiscoveryChips(onSearch: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(listOf("Trending" to "trending", "New music" to "new releases", "Hip-hop" to "hip hop", "Afrobeats" to "afrobeats", "Electronic" to "electronic")) { (label, query) ->
            Surface(
                onClick = { onSearch(query) },
                shape = RoundedCornerShape(50),
                color = SurfaceElevated
            ) {
                Text(
                    label,
                    color = if (label == "Trending") NeonCyan else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun MoodChips(onSearch: (String) -> Unit) {
    val moods = listOf("Chill", "Focus", "Workout", "Late night", "Happy", "Melancholy")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(moods) { mood ->
            AssistChip(onClick = { onSearch(mood) }, label = { Text(mood) }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp)) })
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SearchHistoryRow(onSearch: (String) -> Unit) {
    val snapshot by LibraryStore.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    if (snapshot.searchHistory.isEmpty()) return
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { LibraryStore.clearSearchHistory() } }) { Text("Clear") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(snapshot.searchHistory, key = { it }) { q ->
                AssistChip(onClick = { onSearch(q) }, label = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Filled.History, null, modifier = Modifier.size(16.dp)) })
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ArtistRail(title: String, tracks: List<Track>, onSearch: (String) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(tracks, key = { it.artist }) { track ->
                Column(Modifier.width(110.dp).clickable { onSearch(track.artist) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = track.artworkUrl, contentDescription = null, modifier = Modifier.size(96.dp).clip(CircleShape).background(SurfaceElevated))
                    Spacer(Modifier.height(6.dp))
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("Explore", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                }
            }
        }
    }
}

@Composable
private fun FeaturedMixCard(tracks: List<Track>, onPlay: () -> Unit) {
    Card(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SurfaceElevated)) {
        Box(Modifier.height(190.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                val covers = tracks.take(3)
                if (covers.isNotEmpty()) {
                    AsyncImage(model = covers.first().artworkUrl, contentDescription = null, modifier = Modifier.size(132.dp).clip(RoundedCornerShape(20.dp)).background(SurfaceElevated))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your Quick Mix", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("A rotating mix built from what's trending, new and recently played.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play mix")
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicRail(title: String, tracks: List<Track>) {
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                HeroTrackCard(track = track, onClick = { PlayerManager.playQueue(tracks, index) })
            }
        }
    }
}

@Composable
private fun HeroTrackCard(track: Track, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceElevated)
        )
        Spacer(Modifier.height(6.dp))
        Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DiscoverTab(
    state: MusicUiState,
    onSearch: (String) -> Unit
) {
    val pools = listOf(state.trending, state.newReleases, state.countryTracks).flatten().distinctBy { it.id }
    val samples = if (pools.isNotEmpty()) pools else state.tracks
    var sampleIndex by remember(samples.map { it.id }) { mutableIntStateOf(0) }
    val sample = samples.getOrNull(sampleIndex.coerceIn(0, (samples.size - 1).coerceAtLeast(0)))
    val genres = listOf(
        "Afrobeats", "Hip-hop", "Electronic", "R&B", "Jazz", "Rock", "Chill", "Instrumental", "Focus", "Workout"
    )
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Discover", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Find something new without leaving your flow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("Quick discovery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(genres) { genre ->
                    FilterChip(selected = false, onClick = { onSearch(genre) }, label = { Text(genre) })
                }
            }
        }
        if (sample != null) {
            item {
                Card(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SurfaceElevated)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Samples", style = MaterialTheme.typography.labelLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        AsyncImage(model = sample.artworkUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1.55f).clip(RoundedCornerShape(18.dp)).background(SurfaceElevated))
                        Spacer(Modifier.height(12.dp))
                        Text(sample.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sample.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { PlayerManager.play(sample) }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Play") }
                            OutlinedButton(onClick = { sampleIndex = (sampleIndex + 1) % samples.size }) { Icon(Icons.Filled.SkipNext, null); Spacer(Modifier.width(5.dp)); Text("Next") }
                        }
                    }
                }
            }
        }
        if (samples.isNotEmpty()) {
            item {
                Text("Fresh picks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            itemsIndexed(samples.take(12), key = { _, t -> t.id }) { index, track ->
                TrackRow(track = track, onPlay = { PlayerManager.playQueue(samples, index) }, onDownload = null, radioTracks = samples)
            }
        } else {
            item { EmptyState("Start with a genre or search for an artist, song or mood.") }
        }
    }
}

@Composable
private fun DiscoveryFeatureCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        Modifier.padding(16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = NeonCyan.copy(alpha = 0.14f)) {
                Icon(icon, null, tint = NeonCyan, modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TrackDetailsDialog(track: Track, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Track details") },
        text = {
            Column {
                AsyncImage(model = track.artworkUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(SurfaceElevated))
                Spacer(Modifier.height(14.dp))
                Text(track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("Source: ${track.source}")
                Text("Duration: ${track.durationSeconds?.let { "%d:%02d".format(it / 60, it % 60) } ?: "Unknown"}")
                Text("License: ${track.licenseNote}")
                Spacer(Modifier.height(6.dp))
                Text("Source: ${track.source.name}", style = MaterialTheme.typography.labelMedium)
                Text("Stream: ${track.streamUrl}", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                track.downloadUrl?.let { Text("Download: $it", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    )
}

@Composable
private fun PlaylistPickerDialog(
    track: Track,
    playlists: List<Playlist>,
    onCreate: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var createMode by remember { mutableStateOf(playlists.isEmpty()) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        confirmButton = {
            if (createMode) {
                TextButton(onClick = { if (name.isNotBlank()) { onCreate(name); onDismiss() } }) { Text("Create") }
            } else TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = { if (createMode.not()) TextButton(onClick = { createMode = true }) { Text("New playlist") } },
        text = {
            Column {
                if (createMode) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") }, singleLine = true)
                } else {
                    playlists.forEach { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.tracks.size} tracks") },
                            leadingContent = { Icon(Icons.Filled.QueueMusic, null) },
                            modifier = Modifier.clickable { onAdd(playlist.id); onDismiss() }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun PodcastTab(
    state: PodcastUiState,
    onSearch: (String) -> Unit,
    onOpenShow: (PodcastShow) -> Unit,
    onBack: () -> Unit,
    onSetCountry: (String) -> Unit
) {
    val context = LocalContext.current
    val downloader = remember { Downloader(context) }
    val scope = rememberCoroutineScope()
    val show = state.selectedShow

    Column(Modifier.fillMaxSize()) {
        if (show == null) {
            SearchField(value = state.query, placeholder = "Search podcasts worldwide…", onSearch = onSearch)
            CountryChipRow(options = FEATURED_COUNTRIES, selected = state.country, onSelect = onSetCountry)
        } else {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(show.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        when {
            state.loadState == LoadState.LOADING -> ShimmerList()
            show == null && state.shows.isEmpty() -> EmptyState("Search for a show, or switch storefronts above — trending charts load automatically")
            show == null -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(state.shows, key = { it.id }) { s ->
                    PodcastShowRow(s, onClick = { onOpenShow(s) })
                }
            }
            state.episodes.isEmpty() -> EmptyState("Couldn't read an episode list from this show's feed")
            else -> {
                val episodeTracks = state.episodes.map { it.toTrack() }
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(state.episodes, key = { _, ep -> ep.id }) { index, ep ->
                        EpisodeRow(
                            episode = ep,
                            onPlay = { PlayerManager.playQueue(episodeTracks, index) },
                            onDownload = { scope.launch { downloader.downloadEpisode(ep) } }
                        )
                    }
                }
            }
        }
    }
}

private fun PodcastEpisode.toTrack(): Track = Track(
    id = id,
    title = episodeTitle,
    artist = podcastTitle,
    artworkUrl = artworkUrl,
    streamUrl = audioUrl,
    downloadUrl = audioUrl,
    durationSeconds = durationSeconds,
    source = Track.Source.PODCAST,
    language = null,
    licenseNote = "Podcast episode"
)

@Composable
private fun CountryChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options, key = { it.first }) { (code, name) ->
            FilterChip(
                selected = code == selected,
                onClick = { onSelect(code) },
                label = { Text(name) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SearchField(value: String, placeholder: String, onSearch: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch(text) }),
        trailingIcon = {
            IconButton(onClick = { onSearch(text) }) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Search")
            }
        }
    )
}

@Composable
private fun TrackRow(track: Track, onPlay: () -> Unit, onDownload: (() -> Unit)?, radioTracks: List<Track> = emptyList()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val librarySnapshot by LibraryStore.snapshot.collectAsState()
    val alreadyDownloaded = librarySnapshot.entries.any { it.id == track.id && it.status == DownloadStatus.COMPLETE }
    val favorite = track.id in librarySnapshot.favoriteIds
    var menuOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var playlistOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceElevated)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SourceBadge(track.source)
        }
        if (alreadyDownloaded) {
            Icon(Icons.Filled.DownloadDone, contentDescription = "Already downloaded", tint = NeonCyan, modifier = Modifier.size(18.dp))
        } else if (onDownload != null) {
            IconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = "Download", tint = NeonCyan)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = { Icon(Icons.Filled.PlaylistPlay, null) },
                    onClick = { PlayerManager.playNext(track); menuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.Filled.QueueMusic, null) },
                    onClick = { PlayerManager.addToQueue(track); menuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text(if (favorite) "Remove favorite" else "Add to favorites") },
                    leadingIcon = { Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null) },
                    onClick = {
                        scope.launch {
                            val adding = track.id !in librarySnapshot.favoriteIds
                            LibraryStore.toggleFavorite(track)
                            if (adding && librarySnapshot.smartDownloads && track.isDownloadable) downloader.downloadTrack(track)
                        }
                        menuOpen = false
                    }
                )
                if (onDownload != null && !alreadyDownloaded) {
                    DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Filled.Download, null) },
                        onClick = { onDownload(); menuOpen = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) },
                    onClick = { playlistOpen = true; menuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text(if (track.artist in librarySnapshot.followedArtists) "Unfollow artist" else "Follow artist") },
                    leadingIcon = { Icon(Icons.Filled.PersonAdd, null) },
                    onClick = { scope.launch { LibraryStore.toggleFollowArtist(track.artist) }; menuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text("Start track radio") },
                    leadingIcon = { Icon(Icons.Filled.Radio, null) },
                    onClick = {
                        val pool = radioTracks.distinctBy { it.id }
                        val radio = pool.filter { it.artist.equals(track.artist, ignoreCase = true) }.ifEmpty { pool }
                        PlayerManager.playQueue(if (radio.isEmpty()) listOf(track) else radio.shuffled(), 0)
                        menuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}\n${track.streamUrl}")
                        }
                        context.startActivity(Intent.createChooser(send, "Share track"))
                        menuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Track details") },
                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                    onClick = { detailsOpen = true; menuOpen = false }
                )
            }
        }
    }
    if (detailsOpen) {
        TrackDetailsDialog(track = track, onDismiss = { detailsOpen = false })
    }
    if (playlistOpen) {
        PlaylistPickerDialog(
            track = track,
            playlists = librarySnapshot.playlists,
            onCreate = { name -> scope.launch { LibraryStore.createPlaylist(name) } },
            onAdd = { playlistId -> scope.launch { LibraryStore.addToPlaylist(playlistId, track) } },
            onDismiss = { playlistOpen = false }
        )
    }
}

@Composable
private fun PodcastShowRow(show: PodcastShow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = show.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceElevated)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(show.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            show.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun EpisodeRow(episode: PodcastEpisode, onPlay: () -> Unit, onDownload: () -> Unit) {
    val librarySnapshot by LibraryStore.snapshot.collectAsState()
    val alreadyDownloaded = librarySnapshot.entries.any { it.id == episode.id && it.status == DownloadStatus.COMPLETE }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(episode.episodeTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (alreadyDownloaded) {
            Icon(Icons.Filled.DownloadDone, contentDescription = "Already downloaded", tint = NeonCyan, modifier = Modifier.size(18.dp))
        } else {
            IconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = "Download", tint = NeonCyan)
            }
        }
    }
}

@Composable
private fun SourceBadge(source: Track.Source) {
    val label = when (source) {
        Track.Source.JAMENDO -> "Jamendo · CC"
        Track.Source.ARCHIVE_ORG -> "Internet Archive"
        Track.Source.OPENVERSE -> "Openverse"
        Track.Source.PODCAST -> "Podcast"
        Track.Source.LOCAL_IMPORT -> "This device"
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = NeonPurple,
        modifier = Modifier
            .padding(top = 2.dp)
            .background(SurfaceElevated, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun MiniPlayerBar(track: Track, isPlaying: Boolean, onExpand: () -> Unit, onQueue: () -> Unit) {
    var position by remember(track.id) { mutableFloatStateOf(0f) }
    var duration by remember(track.id) { mutableFloatStateOf(0f) }

    LaunchedEffect(track.id, isPlaying) {
        while (true) {
            position = PlayerManager.currentPositionMs().toFloat()
            val d = PlayerManager.durationMs().toFloat()
            if (d > 0f) duration = d
            delay(1000)
        }
    }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .background(Brush.horizontalGradient(listOf(SurfaceElevated, NeonPurple.copy(alpha = 0.15f))))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceElevated)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            EqualizerBars(playing = isPlaying, modifier = Modifier.padding(end = 10.dp))
            IconButton(onClick = onQueue) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = NeonCyan)
            }
            IconButton(
                onClick = { PlayerManager.togglePlayPause() },
                modifier = Modifier.clip(CircleShape).background(NeonCyan)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = SurfaceElevated
                )
            }
        }
        LinearProgressIndicator(
            progress = { if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = NeonCyan,
            trackColor = SurfaceElevated
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
