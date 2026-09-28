package com.ciphertun.aetherwave.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Jamendo API v3.0 — https://developer.jamendo.com
 *
 * Catalogue of Creative Commons-licensed tracks from independent artists
 * worldwide. Auth is a simple client_id query param (free, instant sign-up,
 * no OAuth needed for read-only catalogue search).
 *
 * IMPORTANT: since Feb 2021 Jamendo lets each artist opt in/out of allowing
 * downloads. We only surface a downloadUrl when [JamendoTrack.audiodownloadAllowed]
 * is true — see JamendoMapper.
 *
 * `audioformat` and `audiodlformat` are separate on purpose: streaming uses
 * mp32 (320kbps VBR — small, starts fast) while downloads request `flac`
 * (Jamendo's highest tier) via `audiodlformat`, per the documented
 * audiodlformat parameter at developer.jamendo.com/v3.0/tracks. Not every
 * artist necessarily has a flac master uploaded, so this is "best available
 * up to lossless", not a guarantee every track downloads as true FLAC.
 */
interface JamendoApi {

    @GET("v3.0/tracks/")
    suspend fun searchTracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 30,
        @Query("namesearch") query: String,
        @Query("include") include: String = "musicinfo",
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("audiodlformat") audioDlFormat: String = "flac"
    ): JamendoResponse

    @GET("v3.0/tracks/")
    suspend fun trending(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 30,
        @Query("order") order: String = "popularity_month",
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("audiodlformat") audioDlFormat: String = "flac"
    ): JamendoResponse

    /** Newest uploads first — "New Releases" rail. Freshness is bounded by
     * what artists have actually uploaded to Jamendo, not global chart data. */
    @GET("v3.0/tracks/")
    suspend fun newest(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 30,
        @Query("order") order: String = "releasedate_desc",
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("audiodlformat") audioDlFormat: String = "flac"
    ): JamendoResponse

    /**
     * Artists who've self-declared a home country — the only source in this
     * app with genuine per-country data (see developer.jamendo.com/v3.0/artists/locations).
     * [countryCode3] is ISO 3166-1 **alpha-3** (e.g. "ZAF", not "ZA").
     */
    @GET("v3.0/artists/locations/")
    suspend fun artistsInCountry(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("location_country") countryCode3: String,
        @Query("haslocation") hasLocation: Boolean = true,
        @Query("limit") limit: Int = 40
    ): JamendoArtistLocationResponse

    /** Tracks belonging to a specific set of artist IDs — paired with [artistsInCountry]. */
    @GET("v3.0/artists/tracks/")
    suspend fun tracksByArtists(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("id") artistIds: List<String>,
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("audiodlformat") audioDlFormat: String = "flac"
    ): JamendoArtistTracksResponse
}

@Serializable
data class JamendoResponse(
    @SerialName("results") val results: List<JamendoTrack> = emptyList()
)

@Serializable
data class JamendoTrack(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("album_image") val albumImage: String? = null,
    @SerialName("audio") val audio: String,
    @SerialName("audiodownload") val audioDownload: String? = null,
    @SerialName("audiodownload_allowed") val audioDownloadAllowed: Boolean = false,
    @SerialName("duration") val durationSeconds: Int? = null,
    @SerialName("license_ccurl") val licenseCcUrl: String? = null
)

@Serializable
data class JamendoArtistLocationResponse(
    @SerialName("results") val results: List<JamendoArtistLocation> = emptyList()
)

@Serializable
data class JamendoArtistLocation(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null
)

/** /v3.0/artists/tracks/ groups results by artist, each with its own nested track list. */
@Serializable
data class JamendoArtistTracksResponse(
    @SerialName("results") val results: List<JamendoArtistWithTracks> = emptyList()
)

@Serializable
data class JamendoArtistWithTracks(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("tracks") val tracks: List<JamendoTrack> = emptyList()
)
