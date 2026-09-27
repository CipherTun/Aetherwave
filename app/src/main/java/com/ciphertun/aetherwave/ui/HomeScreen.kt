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
import com.ciphertun.aetherwave.model.PodcastEpisode
import com.ciphertun.aetherwave.model.PodcastShow
import com.ciphertun.aetherwave.model.Track
import com.ciphertun.aetherwave.playback.PlayerManager
import com.ciphertun.aetherwave.ui.theme.NeonCyan
import com.ciphertun.aetherwave.ui.theme.NeonPurple
import com.ciphertun.aetherwave.ui.theme.SurfaceElevated
import kotlinx.coroutines.launch

private enum class Tab { MUSIC, PODCASTS, LIBRARY }

@Composable
fun HomeScreen(viewModel: SearchViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.MUSIC) }
    var playerExpanded by remember { mutableStateOf(false) }
    val musicState by viewModel.music.collectAsState()
    val podcastState by viewModel.podcasts.collectAsState()
    val nowPlaying by PlayerManager.nowPlaying.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()
    val librarySnapshot by LibraryStore.snapshot.collectAsState()
    val scope = rememberCoroutineScope()

    // Polls DownloadManager for real progress/completion regardless of which
    // tab is showing, so a download finished in the background (or while the
    // user was on a different tab) still shows up promptly everywhere —
    // the "already downloaded" badge on search rows included.
    LaunchedEffect(librarySnapshot.entries.any { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RUNNING }) {
        while (librarySnapshot.entries.any { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RUNNING }) {
            kotlinx.coroutines.delay(1200)
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
                            selected = tab == Tab.MUSIC,
                            onClick = { tab = Tab.MUSIC },
                            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Music") },
                            label = { Text("Music") }
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
                            icon = { Icon(Icons.Filled.Download, contentDescription = "Library") },
                            label = { Text("Library") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tab.MUSIC -> MusicTab(musicState, onSearch = viewModel::searchMusic)
                    Tab.PODCASTS -> PodcastTab(
                        state = podcastState,
                        onSearch = viewModel::searchPodcasts,
                        onOpenShow = viewModel::openPodcast,
                        onBack = viewModel::closePodcast,
                        onSetCountry = viewModel::setCountry
                    )
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
                NowPlayingScreen(
                    track = track,
                    isPlaying = isPlaying,
                    isFavorite = isFavorite,
                    isDownloadable = track.isDownloadable,
                    onToggleFavorite = { scope.launch { LibraryStore.toggleFavorite(track.id) } },
                    onDownload = { scope.launch { downloader.downloadTrack(track) } },
                    onCollapse = { playerExpanded = false }
                )
            }
        }
    }
}

@Composable
private fun MusicTab(state: MusicUiState, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    val downloader = remember { Downloader(context) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = state.query,
            placeholder = "Search Jamendo + Internet Archive…",
            onSearch = onSearch
        )
        when {
            state.loadState == LoadState.LOADING -> ShimmerList()
            state.tracks.isEmpty() -> EmptyState("No tracks found — try another search")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(state.tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onPlay = { PlayerManager.play(track) },
                        onDownload = if (track.isDownloadable) {
                            { scope.launch { downloader.downloadTrack(track) } }
                        } else null
                    )
                }
            }
        }
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
            CountryChipRow(selected = state.country, onSelect = onSetCountry)
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
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(state.episodes, key = { it.id }) { ep ->
                    EpisodeRow(
                        episode = ep,
                        onPlay = {
                            PlayerManager.play(
                                Track(
                                    id = ep.id,
                                    title = ep.episodeTitle,
                                    artist = ep.podcastTitle,
                                    artworkUrl = ep.artworkUrl,
                                    streamUrl = ep.audioUrl,
                                    downloadUrl = ep.audioUrl,
                                    durationSeconds = ep.durationSeconds,
                                    source = Track.Source.PODCAST,
                                    language = null,
                                    licenseNote = "Podcast episode"
                                )
                            )
                        },
                        onDownload = { scope.launch { downloader.downloadEpisode(ep) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryChipRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(FEATURED_COUNTRIES, key = { it.first }) { (code, name) ->
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
        Track.Source.PODCAST -> "Podcast"
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
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
