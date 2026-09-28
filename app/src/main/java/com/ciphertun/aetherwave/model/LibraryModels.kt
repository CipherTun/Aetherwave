package com.ciphertun.aetherwave.model

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus { PENDING, RUNNING, COMPLETE, FAILED }

/**
 * One entry in the in-app Library tab. Created the moment a download is
 * enqueued (status = PENDING) so the UI has something to show immediately;
 * [LibraryStore] fills in [localUri]/[progressPercent] as Android's
 * DownloadManager reports real status.
 *
 * [localUri] is a content:// Uri owned by DownloadManager on our app's
 * behalf — readable by this app (and by ExoPlayer for offline playback)
 * without any extra storage permission, and indexed into the public
 * Music/Podcasts folders so other music apps on the device see the same
 * file.
 */
@Serializable
data class LibraryEntry(
    val id: String,
    val downloadId: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val source: Track.Source,
    val localUri: String? = null,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progressPercent: Int = 0,
    val downloadedAtMillis: Long = System.currentTimeMillis()
)

/** Everything persisted to disk between launches — one small JSON blob. */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val tracks: List<Track> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis()
)

/** Everything persisted to disk between launches — one small JSON blob. */
@Serializable
data class LibrarySnapshot(
    val entries: List<LibraryEntry> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val favoriteTracks: List<Track> = emptyList(),
    val followedArtists: Set<String> = emptySet(),
    val recentlyPlayed: List<Track> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val importedTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val downloadOverWifiOnly: Boolean = false,
    val smartDownloads: Boolean = false
)
