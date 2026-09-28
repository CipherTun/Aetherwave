package com.ciphertun.aetherwave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ciphertun.aetherwave.data.LibraryStore
import com.ciphertun.aetherwave.model.DownloadStatus
import com.ciphertun.aetherwave.model.LibraryEntry
import com.ciphertun.aetherwave.model.Track
import com.ciphertun.aetherwave.playback.PlayerManager
import com.ciphertun.aetherwave.ui.theme.NeonCyan
import com.ciphertun.aetherwave.ui.theme.NeonPink
import com.ciphertun.aetherwave.ui.theme.SurfaceElevated
import kotlinx.coroutines.launch

@Composable
fun LibraryTab() {
    val snapshot by LibraryStore.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var favoritesOnly by remember { mutableStateOf(false) }

    // Real progress polling lives in HomeScreen (runs regardless of which
    // tab is on screen) — this tab just reads whatever LibraryStore has.

    val entries = snapshot.entries
        .filter { !favoritesOnly || it.id in snapshot.favoriteIds }
        .sortedWith(compareBy<LibraryEntry> { it.status != DownloadStatus.COMPLETE }.thenByDescending { it.downloadedAtMillis })

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("Favorites") },
                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = if (favoritesOnly) NeonPink else LocalContentColor.current) }
            )
        }

if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (favoritesOnly) "No favorites yet — heart a downloaded track to pin it here"
                    else "Nothing downloaded yet — tap the download icon on any track or episode",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            val playableTracks = entries
                .filter { it.status == DownloadStatus.COMPLETE && it.localUri != null }
                .map { it.toOfflineTrack() }

            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(entries, key = { it.id }) { entry ->
                    LibraryRow(
                        entry = entry,
                        isFavorite = entry.id in snapshot.favoriteIds,
                        onPlay = {
                            val playIndex = playableTracks.indexOfFirst { it.id == entry.id }
                            if (playIndex >= 0) PlayerManager.playQueue(playableTracks, playIndex)
                        },
                        onToggleFavorite = { scope.launch { LibraryStore.toggleFavorite(entry.id) } },
                        onRemove = { scope.launch { LibraryStore.remove(entry.id) } }
                    )
                }
            }
        }
    }
}

private fun LibraryEntry.toOfflineTrack(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    streamUrl = localUri ?: "",
    downloadUrl = localUri,
    durationSeconds = null,
    source = source,
    language = null,
    licenseNote = "Downloaded — playing offline from this device"
)

@Composable
private fun LibraryRow(
    entry: LibraryEntry,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit
) {
    val playable = entry.status == DownloadStatus.COMPLETE && entry.localUri != null

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = playable, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = entry.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceElevated)
            )
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
                Icon(Icons.Filled.DownloadDone, contentDescription = "Downloaded", tint = NeonCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) NeonPink else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (entry.status == DownloadStatus.RUNNING || entry.status == DownloadStatus.PENDING) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { entry.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = NeonCyan,
                trackColor = SurfaceElevated
            )
        }
    }
}
