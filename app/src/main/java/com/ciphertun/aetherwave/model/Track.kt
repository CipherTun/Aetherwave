package com.ciphertun.aetherwave.model

import kotlinx.serialization.Serializable

/**
 * One playable/downloadable item, normalized from whichever source it came
 * from (Jamendo, Internet Archive, Openverse, or a podcast feed) so the rest
 * of the app never has to think about each source's own response shape.
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
    enum class Source { JAMENDO, ARCHIVE_ORG, OPENVERSE, PODCAST, LOCAL, DIRECT }

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

/**
 * Countries with genuine artist-location data behind them (see
 * JamendoApi.artistsInCountry) — ISO 3166-1 **alpha-3**, which is what
 * Jamendo's location filter expects (not the alpha-2 codes used for the
 * podcast storefront picker).
 */
val FEATURED_MUSIC_COUNTRIES = listOf(
    "ZAF" to "South Africa",
    "NGA" to "Nigeria",
    "USA" to "United States",
    "GBR" to "United Kingdom",
    "DEU" to "Germany",
    "FRA" to "France",
    "BRA" to "Brazil",
    "IND" to "India",
    "JPN" to "Japan",
    "AUS" to "Australia"
)
