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
data class LibrarySnapshot(
    val entries: List<LibraryEntry> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val downloadOverWifiOnly: Boolean = false
)
