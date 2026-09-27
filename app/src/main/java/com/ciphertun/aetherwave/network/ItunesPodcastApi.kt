package com.ciphertun.aetherwave.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Apple's iTunes Search / Lookup API — https://itunes.apple.com
 *
 * Completely public: no API key, no signup, no auth headers. The same
 * endpoint that powers search on podcasts.apple.com. Covers hundreds of
 * thousands of shows across 175+ country storefronts — passing [country]
 * gives genuine per-country browsing, which neither Jamendo nor Archive.org
 * can offer reliably.
 *
 * Only returns metadata + the show's RSS feed URL, never episodes — fetch
 * and parse that feed for the actual episode list (see RssFeedParser).
 */
interface ItunesPodcastApi {

    @GET("search")
    suspend fun searchPodcasts(
        @Query("term") term: String,
        @Query("country") country: String = "US",
        @Query("media") media: String = "podcast",
        @Query("entity") entity: String = "podcast",
        @Query("limit") limit: Int = 25
    ): ItunesSearchResponse

    /** Resolves full metadata (crucially, feedUrl) for a known collection id. */
    @GET("lookup")
    suspend fun lookup(@Query("id") collectionId: Long): ItunesSearchResponse
}

@Serializable
data class ItunesSearchResponse(
    @SerialName("results") val results: List<ItunesPodcastResult> = emptyList()
)

@Serializable
data class ItunesPodcastResult(
    @SerialName("collectionId") val collectionId: Long,
    @SerialName("collectionName") val collectionName: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("artworkUrl600") val artworkUrl600: String? = null,
    @SerialName("artworkUrl100") val artworkUrl100: String? = null,
    @SerialName("feedUrl") val feedUrl: String? = null,
    @SerialName("country") val country: String? = null
)
