package com.ciphertun.aetherwave.network

import android.util.Xml
import com.ciphertun.aetherwave.model.PodcastEpisode
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Podcast RSS feeds are plain public XML — no client library needed. Pulls
 * exactly what a player needs (title, audio enclosure, duration, artwork,
 * publish date) out of <channel><item> entries.
 *
 * Real-world feeds are inconsistent about itunes:duration format and
 * pubDate compliance, so both are best-effort: a parse failure there yields
 * null rather than dropping the whole episode, since the enclosure URL is
 * the only field that's actually required to play/download it.
 */
object RssFeedParser {

    fun parse(input: InputStream, podcastTitle: String, fallbackArtwork: String?): List<PodcastEpisode> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val episodes = mutableListOf<PodcastEpisode>()
        var eventType = parser.eventType
        var inItem = false

        var title: String? = null
        var enclosureUrl: String? = null
        var durationSeconds: Int? = null
        var image: String? = null
        var pubDateMillis: Long? = null

        fun reset() {
            title = null; enclosureUrl = null; durationSeconds = null; image = null; pubDateMillis = null
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "item" -> { inItem = true; reset() }
                    "title" -> if (inItem) title = parser.nextTextSafe()
                    "enclosure" -> if (inItem) enclosureUrl = parser.getAttributeValue(null, "url")
                    "itunes:duration" -> if (inItem) durationSeconds = parseDuration(parser.nextTextSafe())
                    "itunes:image" -> if (inItem) image = parser.getAttributeValue(null, "href")
                    "pubdate" -> if (inItem) pubDateMillis = parsePubDate(parser.nextTextSafe())
                }
                XmlPullParser.END_TAG -> if (parser.name.equals("item", ignoreCase = true) && inItem) {
                    inItem = false
                    enclosureUrl?.let { url ->
                        episodes += PodcastEpisode(
                            id = "podcast:${url.hashCode()}",
                            podcastTitle = podcastTitle,
                            episodeTitle = title ?: "Untitled episode",
                            artworkUrl = image ?: fallbackArtwork,
                            audioUrl = url,
                            durationSeconds = durationSeconds,
                            datePublished = pubDateMillis
                        )
                    }
                }
            }
            eventType = runCatching { parser.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
        }
        return episodes
    }

    private fun XmlPullParser.nextTextSafe(): String? = runCatching { nextText() }.getOrNull()

    private fun parseDuration(raw: String?): Int? {
        if (raw.isNullOrBlank()) return raw?.toIntOrNull()
        val parts = raw.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            1 -> parts.getOrNull(0)
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private fun parsePubDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(raw.trim())).toEpochMilli()
        }.getOrNull()
    }
}
