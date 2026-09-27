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
 */
interface JamendoApi {

    @GET("v3.0/tracks/")
    suspend fun searchTracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 30,
        @Query("namesearch") query: String,
        @Query("include") include: String = "musicinfo",
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse

    @GET("v3.0/tracks/")
    suspend fun trending(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 30,
        @Query("order") order: String = "popularity_month",
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse
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
