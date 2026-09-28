package com.ciphertun.aetherwave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ciphertun.aetherwave.data.LibraryStore
import com.ciphertun.aetherwave.download.Downloader
import com.ciphertun.aetherwave.model.DownloadStatus
import com.ciphertun.aetherwave.model.LibraryEntry
import com.ciphertun.aetherwave.ui.theme.NeonCyan
import com.ciphertun.aetherwave.ui.theme.SurfaceElevated
import kotlinx.coroutines.launch

@Composable
fun DownloadsTab() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snapshot by LibraryStore.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val downloader = remember { Downloader(context) }
    var showDirect by remember { mutableStateOf(false) }

    LaunchedEffect(snapshot.entries.any { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RUNNING }) {
        while (snapshot.entries.any { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RUNNING }) {
            kotlinx.coroutines.delay(900)
            LibraryStore.reconcile()
        }
    }

    val active = snapshot.entries.filter { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RUNNING }
    val completed = snapshot.entries.filter { it.status == DownloadStatus.COMPLETE }
    val failed = snapshot.entries.filter { it.status == DownloadStatus.FAILED }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineMedium)
                Text("${active.size} active · ${completed.size} completed · ${failed.size} failed", style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalButton(onClick = { showDirect = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add URL")
            }
        }

        if (snapshot.entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Your download queue is empty.\nUse Download on permitted media, or add a direct media URL you are authorized to save.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (active.isNotEmpty()) item { SectionTitle("Active") }
                items(active, key = { it.id }) { DownloadRow(it, onDelete = { scope.launch { LibraryStore.remove(it.id) } }) }
                if (failed.isNotEmpty()) item { SectionTitle("Failed") }
                items(failed, key = { it.id }) { entry ->
                    DownloadRow(entry, onDelete = { scope.launch { LibraryStore.remove(entry.id) } }, onRetry = { scope.launch { downloader.retry(entry) } })
                }
                if (completed.isNotEmpty()) item { SectionTitle("Completed") }
                items(completed, key = { it.id }) { DownloadRow(it, onDelete = { scope.launch { LibraryStore.remove(it.id) } }) }
            }
        }
    }

    if (showDirect) {
        DirectDownloadDialog(
            onDismiss = { showDirect = false },
            onDownload = { url, title, artist, ext ->
                scope.launch { downloader.downloadDirect(url, title, artist, ext) }
                showDirect = false
            }
        )
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable private fun DownloadRow(entry: LibraryEntry, onDelete: () -> Unit, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (entry.status) {
                    DownloadStatus.PENDING -> Text("Queued", style = MaterialTheme.typography.labelSmall)
                    DownloadStatus.RUNNING -> Text("Downloading ${entry.progressPercent}%", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    DownloadStatus.COMPLETE -> Text("Saved to device", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    DownloadStatus.FAILED -> Text("Download failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            when (entry.status) {
                DownloadStatus.COMPLETE -> Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = NeonCyan)
                DownloadStatus.FAILED -> onRetry?.let { IconButton(onClick = it) { Icon(Icons.Filled.Refresh, contentDescription = "Retry") } }
                DownloadStatus.PENDING, DownloadStatus.RUNNING -> Icon(Icons.Filled.PauseCircle, contentDescription = null)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
        }
        if (entry.status == DownloadStatus.PENDING || entry.status == DownloadStatus.RUNNING) {
            LinearProgressIndicator(progress = { entry.progressPercent / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = NeonCyan, trackColor = SurfaceElevated)
        }
    }
}

@Composable private fun DirectDownloadDialog(onDismiss: () -> Unit, onDownload: (String, String, String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var ext by remember { mutableStateOf("mp3") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add authorized media URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For direct media files you are permitted to save. Aetherwave does not bypass service restrictions.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(url, { url = it }, label = { Text("Media URL") }, singleLine = true)
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist / source") }, singleLine = true)
                OutlinedTextField(ext, { ext = it }, label = { Text("Extension") }, singleLine = true)
            }
        },
        confirmButton = { Button(enabled = url.startsWith("http"), onClick = { onDownload(url, title, artist, ext) }) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
