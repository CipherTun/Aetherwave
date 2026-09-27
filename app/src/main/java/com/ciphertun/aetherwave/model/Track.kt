package com.ciphertun.aetherwave.model

import kotlinx.serialization.Serializable

/**
 * One playable/downloadable item, normalized from whichever source it came
 * from so the rest of the app never has to think about Jamendo vs. Archive.org
 * vs. Podcast Index shapes.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val streamUrl: String,
    val downloadUrl: String?,
    val durationSeconds: Int?,
    val source: Source,
    /** Language code (ISO-639) when the source exposes one, e.g. "eng", "por", "jpn". */
    val language: String?,
    val licenseNote: String
) {
    @Serializable
    enum class Source { JAMENDO, ARCHIVE_ORG, PODCAST }

    val isDownloadable: Boolean get() = downloadUrl != null
}

data class PodcastEpisode(
    val id: String,
    val podcastTitle: String,
    val episodeTitle: String,
    val artworkUrl: String?,
    val audioUrl: String,
    val durationSeconds: Int?,
    val datePublished: Long?
)
