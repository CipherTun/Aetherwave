package com.ciphertun.aetherwave.data

import com.ciphertun.aetherwave.BuildConfig
import com.ciphertun.aetherwave.model.PodcastEpisode
import com.ciphertun.aetherwave.model.PodcastShow
import com.ciphertun.aetherwave.model.Track
import com.ciphertun.aetherwave.network.ArchiveDoc
import com.ciphertun.aetherwave.network.ItunesPodcastResult
import com.ciphertun.aetherwave.network.NetworkModule
import com.ciphertun.aetherwave.network.OpenverseAudio
import com.ciphertun.aetherwave.network.PREFERRED_ARCHIVE_DOWNLOAD_FORMATS
import com.ciphertun.aetherwave.network.PREFERRED_ARCHIVE_STREAM_FORMATS
import com.ciphertun.aetherwave.network.RssFeedParser
import com.ciphertun.aetherwave.network.archiveDownloadUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request

class MusicRepository(
    private val jamendoApi: com.ciphertun.aetherwave.network.JamendoApi = NetworkModule.jamendoApi,
    private val archiveApi: com.ciphertun.aetherwave.network.ArchiveApi = NetworkModule.archiveApi,
    private val openverseApi: com.ciphertun.aetherwave.network.OpenverseApi = NetworkModule.openverseApi,
    private val itunesApi: com.ciphertun.aetherwave.network.ItunesPodcastApi = NetworkModule.itunesApi,
    private val chartsApi: com.ciphertun.aetherwave.network.AppleChartsApi = NetworkModule.appleChartsApi
) {

    // ---------------------------------------------------------------- music

    /** Searches Jamendo, Internet Archive and Openverse in parallel, merges results. */
    suspend fun searchMusic(query: String): List<Track> = coroutineScope {
        val jamendoDeferred = async { runCatching { searchJamendo(query) }.getOrDefault(emptyList()) }
        val archiveDeferred = async { runCatching { searchArchive(query) }.getOrDefault(emptyList()) }
        val openverseDeferred = async { runCatching { searchOpenverse(query) }.getOrDefault(emptyList()) }
        jamendoDeferred.await() + archiveDeferred.await() + openverseDeferred.await()
    }

    suspend fun trendingMusic(): List<Track> =
        runCatching {
            jamendoApi.trending(clientId = BuildConfig.JAMENDO_CLIENT_ID).results.map { it.toTrack() }
        }.getOrDefault(emptyList())

    /** Newest uploads on Jamendo — not the same thing as current mainstream chart hits, see README. */
    suspend fun newReleases(): List<Track> =
        runCatching {
            jamendoApi.newest(clientId = BuildConfig.JAMENDO_CLIENT_ID).results.map { it.toTrack() }
        }.getOrDefault(emptyList())

    /**
     * Artists who've told Jamendo they're based in [countryCode3] (ISO
     * alpha-3, e.g. "ZAF"). This is the only genuinely country-scoped result
     * set in the app — Archive.org/Openverse have no equivalent field, so a
     * text search still searches everywhere regardless of this filter.
     */
    suspend fun artistsInCountry(countryCode3: String): List<Track> = runCatching {
        val artistIds = jamendoApi.artistsInCountry(
            clientId = BuildConfig.JAMENDO_CLIENT_ID,
            countryCode3 = countryCode3
        ).results.map { it.id }
        if (artistIds.isEmpty()) return@runCatching emptyList()

        jamendoApi.tracksByArtists(clientId = BuildConfig.JAMENDO_CLIENT_ID, artistIds = artistIds)
            .results.flatMap { artist -> artist.tracks.map { it.toTrack() } }
    }.getOrDefault(emptyList())

    private suspend fun searchJamendo(query: String): List<Track> =
        jamendoApi.searchTracks(clientId = BuildConfig.JAMENDO_CLIENT_ID, query = query)
            .results.map { it.toTrack() }

    private fun com.ciphertun.aetherwave.network.JamendoTrack.toTrack(): Track = Track(
        id = "jamendo:$id",
        title = name,
        artist = artistName,
        artworkUrl = albumImage,
        streamUrl = audio,
        downloadUrl = if (audioDownloadAllowed) audioDownload else null,
        durationSeconds = durationSeconds,
        source = Track.Source.JAMENDO,
        language = null,
        licenseNote = "Creative Commons — download requested at FLAC quality where the artist has one uploaded (${licenseCcUrl ?: "see Jamendo track page"})"
    )

    private suspend fun searchOpenverse(query: String): List<Track> =
        openverseApi.search(query = query).results.mapNotNull { it.toTrack() }

    private fun OpenverseAudio.toTrack(): Track? {
        val fileUrl = url ?: return null
        return Track(
            id = "openverse:$id",
            title = title?.takeIf { it.isNotBlank() } ?: "Untitled",
            artist = creator?.takeIf { it.isNotBlank() } ?: "Unknown artist",
            artworkUrl = thumbnail,
            streamUrl = fileUrl,
            // Openverse indexes exactly one file per item, so that file IS
            // the highest (only) quality version reachable through it.
            downloadUrl = fileUrl,
            durationSeconds = durationMs?.let { (it / 1000).toInt() },
            source = Track.Source.OPENVERSE,
            language = null,
            licenseNote = "Openly licensed (${license?.uppercase() ?: "CC"}) via ${provider ?: "Openverse"}"
        )
    }

    /**
     * Archive.org's search endpoint doesn't return file URLs, only
     * identifiers. We resolve two files per result up front — a small lossy
     * one for streaming, and the best lossless master available for
     * download — so the UI can play/download immediately. Costs one extra
     * request per result, acceptable for the modest page sizes used here.
     */
    private suspend fun searchArchive(query: String): List<Track> = coroutineScope {
        val docs = archiveApi.search(query = "$query AND mediatype:(audio)").response.docs
        docs.map { doc -> async { resolveArchiveTrack(doc) } }
            .mapNotNull { it.await() }
    }

    private suspend fun resolveArchiveTrack(doc: ArchiveDoc): Track? = runCatching {
        val files = archiveApi.metadata(doc.identifier).files
        if (files.isEmpty()) return@runCatching null

        val streamFile = files.firstOrNull { f -> PREFERRED_ARCHIVE_STREAM_FORMATS.any { it.equals(f.format, true) } }
            ?: files.firstOrNull { it.name.endsWith(".mp3", ignoreCase = true) }
        val downloadFile = files.firstOrNull { f -> PREFERRED_ARCHIVE_DOWNLOAD_FORMATS.any { it.equals(f.format, true) } }
            ?: streamFile

        val resolvedStream = streamFile ?: downloadFile ?: return@runCatching null
        val streamUrl = archiveDownloadUrl(doc.identifier, resolvedStream.name)
        val downloadUrl = downloadFile?.let { archiveDownloadUrl(doc.identifier, it.name) }
        val isLossless = downloadFile?.format?.let { fmt ->
            fmt.contains("flac", true) || fmt.contains("wav", true) ||
                fmt.contains("aiff", true) || fmt.contains("lossless", true)
        } ?: false

        Track(
            id = "archive:${doc.identifier}",
            title = doc.title ?: doc.identifier,
            artist = doc.creator ?: "Internet Archive",
            artworkUrl = "https://archive.org/services/img/${doc.identifier}",
            streamUrl = streamUrl,
            downloadUrl = downloadUrl,
            durationSeconds = null,
            source = Track.Source.ARCHIVE_ORG,
            language = doc.language,
            licenseNote = if (isLossless) {
                "Public domain / Internet Archive — downloading the original lossless (${downloadFile?.format}) master"
            } else {
                "Public domain / Internet Archive — verify item page for specific terms"
            }
        )
    }.getOrNull()

    // ------------------------------------------------------------ podcasts

    /** [country] is an ISO 3166-1 alpha-2 storefront code, e.g. "US", "ZA", "JP". */
    suspend fun searchPodcasts(query: String, country: String = "US"): List<PodcastShow> =
        runCatching {
            itunesApi.searchPodcasts(term = query, country = country)
                .results.mapNotNull { it.toPodcastShow(country) }
        }.getOrDefault(emptyList())

    /**
     * Apple's chart feed only returns lightweight entries (id + name), not a
     * feed URL — resolve each via [ItunesPodcastApi.lookup] in parallel, same
     * two-step pattern as Archive.org search above.
     */
    suspend fun trendingPodcasts(country: String = "US"): List<PodcastShow> = coroutineScope {
        val chart = runCatching { chartsApi.topPodcasts(country = country.lowercase()) }
            .getOrNull()?.feed?.results ?: return@coroutineScope emptyList()

        chart.map { entry ->
            async {
                val id = entry.id.toLongOrNull() ?: return@async null
                val resolved = runCatching { itunesApi.lookup(id) }.getOrNull()?.results?.firstOrNull()
                resolved?.toPodcastShow(country)
            }
        }.mapNotNull { it.await() }
    }

    private fun ItunesPodcastResult.toPodcastShow(fallbackCountry: String): PodcastShow? {
        val feed = feedUrl ?: return null
        return PodcastShow(
            id = "itunes:$collectionId",
            title = collectionName ?: "Untitled show",
            author = artistName,
            artworkUrl = artworkUrl600 ?: artworkUrl100,
            feedUrl = feed,
            country = country ?: fallbackCountry
        )
    }

    suspend fun episodesFor(show: PodcastShow): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(show.feedUrl).build()
            NetworkModule.rssHttpClient.newCall(request).execute().use { response ->
                val body = response.body ?: return@use emptyList<PodcastEpisode>()
                RssFeedParser.parse(body.byteStream(), show.title, show.artworkUrl)
            }
        }.getOrDefault(emptyList())
    }
}
