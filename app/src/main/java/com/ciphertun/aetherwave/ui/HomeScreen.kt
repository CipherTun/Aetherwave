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
import com.ciphertun.aetherwave.model.PodcastShow
import com.ciphertun.aetherwave.model.Track
import com.ciphertun.aetherwave.playback.PlayerManager
import com.ciphertun.aetherwave.ui.theme.NeonCyan
import com.ciphertun.aetherwave.ui.theme.NeonPurple
import com.ciphertun.aetherwave.ui.theme.SurfaceElevated
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

private enum class Tab { HOME, EXPLORE, DOWNLOADS, LIBRARY }

@Composable
fun HomeScreen(viewModel: SearchViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var playerExpanded by remember { mutableStateOf(false) }
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
    val appContext = LocalContext.current
    val smartDownloader = remember { Downloader(appContext) }
    val recentlyPlayed by PlayerManager.recentlyPlayed.collectAsState()

    // Polls DownloadManager for real progress/completion regardless of which
    // tab is showing, so a download finished in the background (or while the
    // user was on a different tab) still shows up promptly everywhere —
    // the "already downloaded" badge on search rows included.
    LaunchedEffect(librarySnapshot.smartDownloadsEnabled, recentlyPlayed) {
        if (librarySnapshot.smartDownloadsEnabled && recentlyPlayed.isNotEmpty()) {
            recentlyPlayed.take(librarySnapshot.smartDownloadLimit.coerceIn(1, 50)).forEach { track ->
                if (!track.isDownloadable) return@forEach
                val exists = LibraryStore.snapshot.value.entries.any { it.id == track.id && (it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.COMPLETE) }
                if (!exists) smartDownloader.downloadTrack(track)
            }
        }
    }

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
                            onExpand = { playerExpanded = true }
                        )
                    }
                    NavigationBar(containerColor = SurfaceElevated) {
                        NavigationBarItem(
                            selected = tab == Tab.HOME,
                            onClick = { tab = Tab.HOME },
                            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = tab == Tab.EXPLORE,
                            onClick = { tab = Tab.EXPLORE },
                            icon = { Icon(Icons.Filled.Explore, contentDescription = "Explore") },
                            label = { Text("Explore") }
                        )
                        NavigationBarItem(
                            selected = tab == Tab.DOWNLOADS,
                            onClick = { tab = Tab.DOWNLOADS },
                            icon = { Icon(Icons.Filled.Download, contentDescription = "Downloads") },
                            label = { Text("Downloads") }
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
                    Tab.HOME -> MusicTab(
                        state = musicState,
                        onSearch = viewModel::searchMusic,
                        onSetCountry = viewModel::setMusicCountry
                    )
                    Tab.EXPLORE -> ExploreTab(
                        musicState = musicState,
                        podcastState = podcastState,
                        onMusicSearch = viewModel::searchMusic,
                        onMusicCountry = viewModel::setMusicCountry,
                        onPodcastSearch = viewModel::searchPodcasts,
                        onOpenPodcast = viewModel::openPodcast,
                        onClosePodcast = viewModel::closePodcast,
                        onPodcastCountry = viewModel::setCountry
                    )
                    Tab.DOWNLOADS -> DownloadsTab()
                    Tab.LIBRARY -> LibraryTab()
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
                    onToggleFavorite = { scope.launch { LibraryStore.toggleFavorite(track.id) } },
                    onDownload = { scope.launch { downloader.downloadTrack(track) } },
                    onCollapse = { playerExpanded = false },
                    onSkipNext = { PlayerManager.skipNext() },
                    onSkipPrevious = { PlayerManager.skipPrevious() },
                    onToggleShuffle = { PlayerManager.toggleShuffle() },
                    onCycleRepeat = { PlayerManager.cycleRepeatMode() },
                    onSetSpeed = { PlayerManager.setPlaybackSpeed(it) },
                    onScheduleSleepTimer = { PlayerManager.scheduleSleepTimer(it) },
                    onCancelSleepTimer = { PlayerManager.cancelSleepTimer() }
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
private fun ExploreTab(
    musicState: MusicUiState,
    podcastState: PodcastUiState,
    onMusicSearch: (String) -> Unit,
    onMusicCountry: (String) -> Unit,
    onPodcastSearch: (String) -> Unit,
    onOpenPodcast: (PodcastShow) -> Unit,
    onClosePodcast: () -> Unit,
    onPodcastCountry: (String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            SegmentedButton(selected = mode == 0, onClick = { mode = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Music") }
            SegmentedButton(selected = mode == 1, onClick = { mode = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Podcasts") }
        }
        if (mode == 0) MusicTab(musicState, onMusicSearch, onMusicCountry)
        else PodcastTab(podcastState, onPodcastSearch, onOpenPodcast, onClosePodcast, onPodcastCountry)
    }
}

@Composable
private fun MusicTab(state: MusicUiState, onSearch: (String) -> Unit, onSetCountry: (String) -> Unit) {
    val context = LocalContext.current
    val downloader = remember { Downloader(context) }
    val scope = rememberCoroutineScope()
    val recentlyPlayed by PlayerManager.recentlyPlayed.collectAsState()
    val isBrowsingHome = state.query.isBlank()
    var sourceFilter by rememberSaveable { mutableStateOf("ALL") }
    val filteredTracks = remember(state.tracks, sourceFilter) {
        if (sourceFilter == "ALL") state.tracks else state.tracks.filter { it.source.name == sourceFilter }
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = state.query,
            placeholder = "Search Jamendo + Internet Archive + Openverse…",
            onSearch = onSearch
        )
        when {
            state.loadState == LoadState.LOADING && isBrowsingHome && state.trending.isEmpty() -> ShimmerList()
            !isBrowsingHome && state.loadState == LoadState.LOADING -> ShimmerList()
            !isBrowsingHome && state.tracks.isEmpty() -> EmptyState("No tracks found — try another search")
            !isBrowsingHome -> {
                Column {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf("ALL" to "All", "JAMENDO" to "Jamendo", "ARCHIVE_ORG" to "Archive", "OPENVERSE" to "Openverse"), key = { it.first }) { (code, label) ->
                            FilterChip(selected = sourceFilter == code, onClick = { sourceFilter = code }, label = { Text(label) })
                        }
                    }
                    if (filteredTracks.isEmpty()) EmptyState("No results for this source") else LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        itemsIndexed(filteredTracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onPlay = { PlayerManager.playQueue(filteredTracks, index) },
                        onDownload = if (track.isDownloadable) {
                            { scope.launch { downloader.downloadTrack(track) } }
                        } else null
                        }
                    }
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
                    if (recentlyPlayed.isNotEmpty()) {
                        item { MusicRail(title = "Recently played", tracks = recentlyPlayed) }
                    }
                    if (state.newReleases.isNotEmpty()) {
                        item { MusicRail(title = "New releases on Jamendo", tracks = state.newReleases) }
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
private fun TrackRow(track: Track, onPlay: () -> Unit, onDownload: (() -> Unit)?) {
    val librarySnapshot by LibraryStore.snapshot.collectAsState()
    val alreadyDownloaded = librarySnapshot.entries.any { it.id == track.id && it.status == DownloadStatus.COMPLETE }

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
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceElevated)
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
        Track.Source.LOCAL -> "On device"
        Track.Source.DIRECT -> "Direct download"
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
private fun MiniPlayerBar(track: Track, isPlaying: Boolean, onExpand: () -> Unit) {
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
