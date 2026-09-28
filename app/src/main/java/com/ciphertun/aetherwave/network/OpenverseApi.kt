package com.ciphertun.aetherwave.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Openverse API — https://api.openverse.org (maintained by the WordPress
 * Foundation; formerly the Creative Commons Catalog API).
 *
 * Doesn't host its own audio — it's a search index *across* other openly
 * licensed sources (Wikimedia Commons, ccMixter, Free Music Archive,
 * Freesound and others), so one query here adds all of those into the
 * app's reach without a separate integration per source. Anonymous access
 * is keyless and free; results are capped to open licenses only.
 *
 * There's no quality-tier parameter like Jamendo's — each result's `url`
 * is the one file Openverse indexed for it, so that's both the stream and
 * the download URL: whatever it is, it's already the only (and therefore
 * "highest") version available through this API.
 */
interface OpenverseApi {

    @GET("v1/audio/")
    suspend fun search(
        @Query("q") query: String,
        @Query("category") category: String = "music",
        // cc0/pdm = public domain, by/by-sa = attribution (no-derivatives and
        // non-commercial licenses are deliberately excluded here — fine for
        // personal listening, but a poor fit for a "download it" button).
        @Query("license") license: String = "cc0,pdm,by,by-sa",
        @Query("page_size") pageSize: Int = 25
    ): OpenverseSearchResponse
}

@Serializable
data class OpenverseSearchResponse(
    @SerialName("results") val results: List<OpenverseAudio> = emptyList()
)

@Serializable
data class OpenverseAudio(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String? = null,
    @SerialName("creator") val creator: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("thumbnail") val thumbnail: String? = null,
    @SerialName("duration") val durationMs: Long? = null,
    @SerialName("license") val license: String? = null,
    @SerialName("provider") val provider: String? = null
)
