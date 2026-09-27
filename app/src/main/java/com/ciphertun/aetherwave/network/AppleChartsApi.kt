package com.ciphertun.aetherwave.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Apple's public chart-generator feed — https://rss.marketingtools.apple.com
 * No auth. Gives the current top-podcasts chart for any of Apple's 175+
 * country storefronts. Only carries lightweight metadata + a numeric id, not
 * a feed URL, so results still need one [ItunesPodcastApi.lookup] call each
 * to resolve a playable RSS feed — same two-step pattern already used for
 * Internet Archive search results.
 */
interface AppleChartsApi {
    @GET("{country}/podcasts/top/{limit}/podcasts.json")
    suspend fun topPodcasts(
        @Path("country") country: String = "us",
        @Path("limit") limit: Int = 20
    ): AppleChartsResponse
}

@Serializable
data class AppleChartsResponse(@SerialName("feed") val feed: AppleChartsFeed = AppleChartsFeed())

@Serializable
data class AppleChartsFeed(@SerialName("results") val results: List<AppleChartEntry> = emptyList())

@Serializable
data class AppleChartEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("artworkUrl100") val artworkUrl100: String? = null
)
