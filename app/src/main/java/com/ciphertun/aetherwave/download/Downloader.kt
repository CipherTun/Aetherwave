package com.ciphertun.aetherwave.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.ciphertun.aetherwave.data.LibraryStore
import com.ciphertun.aetherwave.model.DownloadStatus
import com.ciphertun.aetherwave.model.LibraryEntry
import com.ciphertun.aetherwave.model.PodcastEpisode
import com.ciphertun.aetherwave.model.Track

/**
 * Hands the actual bytes off to Android's own DownloadManager system service
 * rather than a hand-rolled downloader — it already handles retries,
 * notifications, and resuming, and writes to the public Music/Podcasts
 * folders without needing storage permission on modern Android.
 *
 * Every download is registered with [LibraryStore] the moment it's
 * enqueued, so the in-app Library tab shows it immediately (as "pending")
 * and can later play it back straight from disk with no network call.
 *
 * Only ever called with URLs the source has confirmed are legally
 * downloadable (see MusicRepository — Jamendo tracks are filtered by
 * audiodownload_allowed, Archive.org items are inherently public files,
 * podcast enclosures are public by the nature of RSS).
 */
class Downloader(private val context: Context) {

    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun downloadTrack(track: Track): Long? {
        val url = track.downloadUrl ?: return null
        val ext = guessExtension(url)
        val downloadId = enqueue(
            url = url,
            title = track.title,
            description = track.artist,
            subDir = Environment.DIRECTORY_MUSIC,
            fileName = safeFileName(track.artist, track.title, ext),
            mimeType = mimeTypeFor(ext)
        )
        LibraryStore.addPending(
            LibraryEntry(
                id = track.id,
                downloadId = downloadId,
                title = track.title,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                source = track.source,
                status = DownloadStatus.PENDING
            )
        )
        return downloadId
    }

    suspend fun downloadEpisode(episode: PodcastEpisode): Long {
        val ext = guessExtension(episode.audioUrl)
        val downloadId = enqueue(
            url = episode.audioUrl,
            title = episode.episodeTitle,
            description = episode.podcastTitle,
            subDir = Environment.DIRECTORY_PODCASTS,
            fileName = safeFileName(episode.podcastTitle, episode.episodeTitle, ext),
            mimeType = mimeTypeFor(ext)
        )
        LibraryStore.addPending(
            LibraryEntry(
                id = episode.id,
                downloadId = downloadId,
                title = episode.episodeTitle,
                artist = episode.podcastTitle,
                artworkUrl = episode.artworkUrl,
                source = Track.Source.PODCAST,
                status = DownloadStatus.PENDING
            )
        )
        return downloadId
    }

    private fun enqueue(
        url: String,
        title: String,
        description: String,
        subDir: String,
        fileName: String,
        mimeType: String
    ): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription(description)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(subDir, fileName)
            // A correct (not just assumed-mp3) MIME type so other music
            // apps' MediaStore queries both find this file AND know how to
            // decode it.
            .setMimeType(mimeType)

        return manager.enqueue(request)
    }

    private fun safeFileName(artist: String, title: String, extension: String): String {
        val raw = "$artist - $title.$extension"
        return raw.replace(Regex("[^A-Za-z0-9 ._-]"), "_")
    }

    /** Reads the file extension straight off the URL; falls back to mp3 if it's missing/unrecognizable. */
    private fun guessExtension(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) ext else "mp3"
    }

    private fun mimeTypeFor(extension: String): String = when (extension) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac", "m4b" -> "audio/mp4"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "opus" -> "audio/opus"
        else -> "audio/mpeg"
    }
}
