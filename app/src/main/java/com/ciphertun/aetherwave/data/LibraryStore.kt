package com.ciphertun.aetherwave.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ciphertun.aetherwave.model.DownloadStatus
import com.ciphertun.aetherwave.model.LibraryEntry
import com.ciphertun.aetherwave.model.LibrarySnapshot
import com.ciphertun.aetherwave.model.Playlist
import com.ciphertun.aetherwave.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the downloads library + favorites as one small JSON file under
 * the app's private storage. Deliberately not Room/SQLite — a few hundred
 * entries at most, read/written as a whole, and it keeps the dependency
 * list short. Swap for Room if the library ever grows into the thousands.
 *
 * The actual audio bytes never live here — [LibraryEntry.localUri] just
 * points at the file DownloadManager already saved into the public
 * Music/Podcasts folder (see Downloader.kt).
 */
object LibraryStore {

    private lateinit var appContext: Context
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _snapshot = MutableStateFlow(LibrarySnapshot())
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _snapshot.value = readFromDisk()
    }

    private fun file(): File = File(appContext.filesDir, "library.json")

    private fun readFromDisk(): LibrarySnapshot = runCatching {
        val f = file()
        if (!f.exists()) return LibrarySnapshot()
        json.decodeFromString(LibrarySnapshot.serializer(), f.readText())
    }.getOrDefault(LibrarySnapshot())

    private suspend fun persist(next: LibrarySnapshot) = mutex.withLock {
        _snapshot.value = next
        withContext(Dispatchers.IO) {
            runCatching { file().writeText(json.encodeToString(LibrarySnapshot.serializer(), next)) }
        }
    }

    suspend fun addPending(entry: LibraryEntry) {
        persist(_snapshot.value.let { it.copy(entries = it.entries + entry) })
    }

    /** Polls DownloadManager for real progress/status of every unfinished entry. */
    suspend fun reconcile() {
        val current = _snapshot.value
        val unfinished = current.entries.filter {
            it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RUNNING
        }
        if (unfinished.isEmpty()) return

        val updates = withContext(Dispatchers.IO) {
            val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(*unfinished.map { it.downloadId }.toLongArray())
            val result = mutableMapOf<Long, LibraryEntry>()

            manager.query(query)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val original = unfinished.firstOrNull { it.downloadId == id } ?: continue

                    val statusCol = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val localUri = if (localUriCol >= 0) cursor.getString(localUriCol) else null

                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else original.progressPercent
                    val newStatus = when (statusCol) {
                        DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETE
                        DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                        DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                        else -> DownloadStatus.PENDING
                    }
                    result[id] = original.copy(
                        status = newStatus,
                        progressPercent = percent,
                        localUri = localUri ?: original.localUri
                    )
                }
            }
            result
        }
        if (updates.isEmpty()) return

        val merged = current.entries.map { updates[it.downloadId] ?: it }
        persist(current.copy(entries = merged))
    }

    suspend fun remove(id: String) {
        val current = _snapshot.value
        val target = current.entries.firstOrNull { it.id == id } ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                (appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(target.downloadId)
            }
        }
        persist(
            current.copy(
                entries = current.entries.filterNot { it.id == id },
                favoriteIds = current.favoriteIds - id,
                favoriteTracks = current.favoriteTracks.filterNot { it.id == id }
            )
        )
    }

    /** Persists a compact listening history so it survives app restarts. */
    fun recordRecentlyPlayed(track: Track) {
        backgroundScope.launch {
            val current = _snapshot.value
            val recent = (listOf(track) + current.recentlyPlayed.filterNot { it.id == track.id }).take(30)
            persist(current.copy(recentlyPlayed = recent))
        }
    }


    suspend fun rememberSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val current = _snapshot.value
        val history = (listOf(clean) + current.searchHistory.filterNot { it.equals(clean, true) }).take(12)
        persist(current.copy(searchHistory = history))
    }

    suspend fun clearSearchHistory() {
        persist(_snapshot.value.copy(searchHistory = emptyList()))
    }

    suspend fun importAudio(uri: Uri): Track? = withContext(Dispatchers.IO) {
        runCatching {
            runCatching { appContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment ?: "Imported audio"
            val title = name.substringBeforeLast('.', name).ifBlank { "Imported audio" }
            val track = Track(
                id = "local:${uri}",
                title = title,
                artist = "Local file",
                artworkUrl = null,
                streamUrl = uri.toString(),
                downloadUrl = null,
                durationSeconds = null,
                source = Track.Source.LOCAL_IMPORT,
                language = null,
                licenseNote = "Imported from this device"
            )
            val current = _snapshot.value
            val tracks = listOf(track) + current.importedTracks.filterNot { it.id == track.id }
            persist(current.copy(importedTracks = tracks.take(500)))
            track
        }.getOrNull()
    }

    suspend fun removeImportedTrack(id: String) {
        persist(_snapshot.value.copy(importedTracks = _snapshot.value.importedTracks.filterNot { it.id == id }))
    }

    suspend fun toggleFavorite(id: String) {
        val current = _snapshot.value
        val next = if (id in current.favoriteIds) current.favoriteIds - id else current.favoriteIds + id
        persist(current.copy(favoriteIds = next))
    }

    /** Persists the track metadata as well as its ID so favorites can be shown even when offline. */
    suspend fun toggleFavorite(track: com.ciphertun.aetherwave.model.Track) {
        val current = _snapshot.value
        val isFavorite = track.id in current.favoriteIds
        val ids = if (isFavorite) current.favoriteIds - track.id else current.favoriteIds + track.id
        val tracks = if (isFavorite) {
            current.favoriteTracks.filterNot { it.id == track.id }
        } else {
            (listOf(track) + current.favoriteTracks.filterNot { it.id == track.id }).take(500)
        }
        persist(current.copy(favoriteIds = ids, favoriteTracks = tracks))
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        persist(_snapshot.value.copy(downloadOverWifiOnly = enabled))
    }

    suspend fun setSmartDownloads(enabled: Boolean) {
        persist(_snapshot.value.copy(smartDownloads = enabled))
    }

    suspend fun toggleFollowArtist(artist: String) {
        val clean = artist.trim()
        if (clean.isBlank()) return
        val current = _snapshot.value
        val next = if (clean in current.followedArtists) current.followedArtists - clean else current.followedArtists + clean
        persist(current.copy(followedArtists = next))
    }

    suspend fun createPlaylist(name: String, description: String = ""): Playlist? {
        val clean = name.trim()
        if (clean.isBlank()) return null
        val playlist = Playlist(
            id = "playlist:${System.currentTimeMillis()}:${clean.hashCode()}",
            name = clean,
            description = description.trim()
        )
        persist(_snapshot.value.copy(playlists = listOf(playlist) + _snapshot.value.playlists))
        return playlist
    }

    suspend fun deletePlaylist(id: String) {
        persist(_snapshot.value.copy(playlists = _snapshot.value.playlists.filterNot { it.id == id }))
    }

    suspend fun addToPlaylist(playlistId: String, track: Track) {
        val updated = _snapshot.value.playlists.map { playlist ->
            if (playlist.id != playlistId || playlist.tracks.any { it.id == track.id }) playlist
            else playlist.copy(tracks = playlist.tracks + track)
        }
        persist(_snapshot.value.copy(playlists = updated))
    }

    suspend fun removeFromPlaylist(playlistId: String, trackId: String) {
        persist(_snapshot.value.copy(playlists = _snapshot.value.playlists.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId }) else playlist
        }))
    }

    suspend fun renamePlaylist(playlistId: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        persist(_snapshot.value.copy(playlists = _snapshot.value.playlists.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(name = clean) else playlist
        }))
    }
}
