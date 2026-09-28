package com.ciphertun.aetherwave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ciphertun.aetherwave.data.LibraryStore
import com.ciphertun.aetherwave.model.DownloadStatus
import com.ciphertun.aetherwave.model.LibraryEntry
import com.ciphertun.aetherwave.model.Playlist
import com.ciphertun.aetherwave.model.Track
import com.ciphertun.aetherwave.playback.PlayerManager
import com.ciphertun.aetherwave.ui.theme.NeonCyan
import com.ciphertun.aetherwave.ui.theme.NeonPink
import com.ciphertun.aetherwave.ui.theme.SurfaceElevated
import kotlinx.coroutines.launch

private enum class LibrarySection { DOWNLOADS, FAVORITES, PLAYLISTS, ARTISTS, RECENT, IMPORTED }

@Composable
fun LibraryTab(onImportAudio: () -> Unit = {}) {
    val snapshot by LibraryStore.snapshot.collectAsState()
    val recentSession by PlayerManager.recentlyPlayed.collectAsState()
    val recentPersisted = snapshot.recentlyPlayed
    val recent = (recentSession + recentPersisted).distinctBy { it.id }.take(30)
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(LibrarySection.DOWNLOADS) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var createPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }

    if (selectedPlaylist != null) {
        PlaylistDetail(
            playlist = snapshot.playlists.firstOrNull { it.id == selectedPlaylist!!.id } ?: selectedPlaylist!!,
            onBack = { selectedPlaylist = null },
            onDelete = { id -> scope.launch { LibraryStore.deletePlaylist(id); selectedPlaylist = null } }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Offline music, favorites and playlists", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onImportAudio) {
                Icon(Icons.Filled.FileOpen, contentDescription = "Import music", tint = NeonCyan)
            }
            IconButton(onClick = { createPlaylist = true }) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = "Create playlist", tint = NeonCyan)
            }
        }

        ScrollableTabRow(selectedTabIndex = section.ordinal, edgePadding = 12.dp) {
            LibrarySection.values().forEach { item ->
                Tab(selected = section == item, onClick = { section = item }, text = {
                    Text(when (item) {
                        LibrarySection.DOWNLOADS -> "Downloads"
                        LibrarySection.FAVORITES -> "Favorites"
                        LibrarySection.PLAYLISTS -> "Playlists"
                        LibrarySection.ARTISTS -> "Artists"
                        LibrarySection.RECENT -> "Recently played"
                        LibrarySection.IMPORTED -> "Imported"
                    })
                })
            }
        }

        when (section) {
            LibrarySection.DOWNLOADS -> DownloadsSection(snapshot, scope)
            LibrarySection.FAVORITES -> FavoritesSection(snapshot, scope)
            LibrarySection.PLAYLISTS -> PlaylistsSection(snapshot.playlists, onOpen = { selectedPlaylist = it }, onDelete = { scope.launch { LibraryStore.deletePlaylist(it) } })
            LibrarySection.ARTISTS -> FollowedArtistsSection(snapshot, scope)
            LibrarySection.RECENT -> RecentSection(recent)
            LibrarySection.IMPORTED -> ImportedSection(snapshot, onImportAudio)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WifiOff, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download over Wi-Fi only", modifier = Modifier.weight(1f))
            Switch(checked = snapshot.downloadOverWifiOnly, onCheckedChange = { scope.launch { LibraryStore.setWifiOnly(it) } })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(18.dp), tint = NeonCyan)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Smart downloads")
                Text("Automatically save new favorites when downloadable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = snapshot.smartDownloads, onCheckedChange = { scope.launch { LibraryStore.setSmartDownloads(it) } })
        }
    }

    if (createPlaylist) {
        AlertDialog(
            onDismissRequest = { createPlaylist = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (playlistName.isNotBlank()) scope.launch { LibraryStore.createPlaylist(playlistName); playlistName = ""; createPlaylist = false }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createPlaylist = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DownloadsSection(snapshot: com.ciphertun.aetherwave.model.LibrarySnapshot, scope: kotlinx.coroutines.CoroutineScope) {
    val entries = snapshot.entries.sortedWith(compareBy<LibraryEntry> { it.status != DownloadStatus.COMPLETE }.thenByDescending { it.downloadedAtMillis })
    if (entries.isEmpty()) return EmptyLibrary("Nothing downloaded yet", "Download music or podcast episodes for offline playback.")
    val playable = entries.filter { it.status == DownloadStatus.COMPLETE && it.localUri != null }.map { it.toOfflineTrack() }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(entries, key = { it.id }) { entry ->
            LibraryRow(entry, entry.id in snapshot.favoriteIds,
                onPlay = { val i = playable.indexOfFirst { it.id == entry.id }; if (i >= 0) PlayerManager.playQueue(playable, i) },
                onToggleFavorite = { scope.launch { LibraryStore.toggleFavorite(entry.id) } },
                onRemove = { scope.launch { LibraryStore.remove(entry.id) } })
        }
    }
}

@Composable
private fun FavoritesSection(snapshot: com.ciphertun.aetherwave.model.LibrarySnapshot, scope: kotlinx.coroutines.CoroutineScope) {
    if (snapshot.favoriteTracks.isEmpty()) return EmptyLibrary("No favorites yet", "Tap the heart on any track to keep it here, even when it isn't downloaded.")
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(snapshot.favoriteTracks, key = { it.id }) { track ->
            Row(Modifier.fillMaxWidth().clickable { PlayerManager.play(track) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, 58)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { scope.launch { LibraryStore.toggleFavorite(track) } }) { Icon(Icons.Filled.Favorite, "Remove favorite", tint = NeonPink) }
            }
        }
    }
}

@Composable
private fun PlaylistsSection(playlists: List<Playlist>, onOpen: (Playlist) -> Unit, onDelete: (String) -> Unit) {
    if (playlists.isEmpty()) return EmptyLibrary("No playlists yet", "Create a playlist from here or use Add to playlist on any track.")
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(playlists, key = { it.id }) { playlist ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(playlist) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(58.dp), shape = RoundedCornerShape(14.dp), color = NeonPurpleTint()) {
                    Icon(Icons.Filled.PlaylistPlay, null, tint = NeonCyan, modifier = Modifier.padding(15.dp).fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${playlist.tracks.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDelete(playlist.id) }) { Icon(Icons.Filled.Delete, "Delete playlist") }
            }
        }
    }
}

@Composable
private fun FollowedArtistsSection(snapshot: com.ciphertun.aetherwave.model.LibrarySnapshot, scope: kotlinx.coroutines.CoroutineScope) {
    val artists = snapshot.followedArtists.toList().sorted()
    if (artists.isEmpty()) return EmptyLibrary("No followed artists", "Follow an artist from a track's menu to keep them here.")
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(artists, key = { it }) { artist ->
            val tracks = snapshot.favoriteTracks.filter { it.artist.equals(artist, true) }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(52.dp), shape = RoundedCornerShape(50), color = SurfaceElevated) {
                    Icon(Icons.Filled.LibraryMusic, null, tint = NeonCyan, modifier = Modifier.padding(14.dp).fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(artist, fontWeight = FontWeight.Bold)
                    Text(if (tracks.isEmpty()) "Followed artist" else "${tracks.size} saved tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (tracks.isNotEmpty()) IconButton(onClick = { PlayerManager.playQueue(tracks, 0) }) { Icon(Icons.Filled.PlaylistPlay, "Play artist") }
                IconButton(onClick = { scope.launch { LibraryStore.toggleFollowArtist(artist) } }) { Icon(Icons.Filled.Delete, "Unfollow") }
            }
        }
    }
}


@Composable
private fun ImportedSection(snapshot: com.ciphertun.aetherwave.model.LibrarySnapshot, onImportAudio: () -> Unit) {
    val scope = rememberCoroutineScope()
    if (snapshot.importedTracks.isEmpty()) return EmptyLibrary("No local music imported", "Bring MP3, M4A or other supported audio files into Aetherwave from your device.")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("On this device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onImportAudio) { Text("Import") }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(snapshot.importedTracks, key = { it.id }) { track ->
                Row(Modifier.fillMaxWidth().clickable { PlayerManager.play(track) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track.artworkUrl, 56)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Local file", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch { LibraryStore.removeImportedTrack(track.id) } }) { Icon(Icons.Filled.Delete, "Remove import") }
                }
            }
        }
    }
}

@Composable
private fun RecentSection(recent: List<Track>) {
    if (recent.isEmpty()) return EmptyLibrary("Nothing played yet", "Tracks you play will appear here during this session.")
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(recent, key = { it.id }) { track ->
            Row(Modifier.fillMaxWidth().clickable { PlayerManager.play(track) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, 56)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetail(playlist: Playlist, onBack: () -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.FavoriteBorder, "Back") }
            Column(Modifier.weight(1f)) { Text(playlist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${playlist.tracks.size} tracks") }
            IconButton(onClick = { onDelete(playlist.id) }) { Icon(Icons.Filled.Delete, "Delete playlist") }
        }
        if (playlist.tracks.isEmpty()) return EmptyLibrary("Playlist is empty", "Add tracks from any track's menu.")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { PlayerManager.playQueue(playlist.tracks, 0) }) { Text("Play") }
            OutlinedButton(onClick = { PlayerManager.playQueue(playlist.tracks.shuffled(), 0) }) { Text("Shuffle") }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(playlist.tracks, key = { it.id }) { track ->
                Row(Modifier.fillMaxWidth().clickable { PlayerManager.play(track) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track.artworkUrl, 52)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(track.title, fontWeight = FontWeight.SemiBold); Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(title: String, message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Artwork(url: String?, size: Int) {
    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceElevated))
}

@Composable
private fun NeonPurpleTint() = com.ciphertun.aetherwave.ui.theme.NeonPurple.copy(alpha = 0.14f)

private fun LibraryEntry.toOfflineTrack(): Track = Track(id, title, artist, artworkUrl, localUri ?: "", localUri, null, source, null, "Downloaded — playing offline from this device")

@Composable
private fun LibraryRow(entry: LibraryEntry, isFavorite: Boolean, onPlay: () -> Unit, onToggleFavorite: () -> Unit, onRemove: () -> Unit) {
    val playable = entry.status == DownloadStatus.COMPLETE && entry.localUri != null
    Column(Modifier.fillMaxWidth().clickable(enabled = playable, onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(entry.artworkUrl, 52)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (entry.status) {
                    DownloadStatus.PENDING -> Text("Waiting to start…", style = MaterialTheme.typography.labelSmall)
                    DownloadStatus.RUNNING -> Text("Downloading ${entry.progressPercent}%…", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    DownloadStatus.FAILED -> Text("Download failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    DownloadStatus.COMPLETE -> Text("Saved on this device", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (entry.status == DownloadStatus.COMPLETE) {
                Icon(Icons.Filled.DownloadDone, "Downloaded", tint = NeonCyan, modifier = Modifier.size(18.dp))
                IconButton(onClick = onToggleFavorite) { Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", tint = if (isFavorite) NeonPink else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove") }
        }
        if (entry.status == DownloadStatus.RUNNING || entry.status == DownloadStatus.PENDING) LinearProgressIndicator(progress = { entry.progressPercent / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = NeonCyan, trackColor = SurfaceElevated)
    }
}
