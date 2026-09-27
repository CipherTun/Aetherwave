package com.ciphertun.aetherwave.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * archive.org public read API — https://archive.org/advancedsearch.php and
 * https://archive.org/metadata/{id}. No API key, no auth. Covers 14M+ audio
 * items worldwide: public-domain recordings, old-time radio in dozens of
 * languages, and the Live Music Archive (etree) — concerts artists explicitly
 * gave permission to record, trade and, in most cases, download.
 *
 * Search only returns identifiers/metadata, not file lists — call
 * [metadata] for a given identifier to resolve an actual playable file.
 */
interface ArchiveApi {

    @GET("advancedsearch.php")
    suspend fun search(
        @Query("q") query: String,
        @Query("fl[]") fields: List<String> = listOf("identifier", "title", "creator", "language", "downloads"),
        @Query("rows") rows: Int = 30,
        @Query("page") page: Int = 1,
        @Query("output") output: String = "json"
    ): ArchiveSearchResponse

    @GET("metadata/{identifier}")
    suspend fun metadata(@Path("identifier") identifier: String): ArchiveMetadataResponse
}

@Serializable
data class ArchiveSearchResponse(
    @SerialName("response") val response: ArchiveSearchInner
)

@Serializable
data class ArchiveSearchInner(
    @SerialName("docs") val docs: List<ArchiveDoc> = emptyList()
)

@Serializable
data class ArchiveDoc(
    @SerialName("identifier") val identifier: String,
    @SerialName("title") val title: String? = null,
    @SerialName("creator") val creator: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("downloads") val downloads: Int? = null
)

@Serializable
data class ArchiveMetadataResponse(
    @SerialName("metadata") val metadata: ArchiveItemMeta? = null,
    @SerialName("files") val files: List<ArchiveFile> = emptyList(),
    @SerialName("server") val server: String? = null,
    @SerialName("dir") val dir: String? = null
)

@Serializable
data class ArchiveItemMeta(
    @SerialName("identifier") val identifier: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("creator") val creator: String? = null
)

@Serializable
data class ArchiveFile(
    @SerialName("name") val name: String,
    @SerialName("format") val format: String? = null,
    @SerialName("length") val length: String? = null
)

/** archive.org serves every uploaded file at this stable pattern. */
fun archiveDownloadUrl(identifier: String, filename: String): String =
    "https://archive.org/download/$identifier/${filename}"

/** Formats archive.org tends to use for compressed, streamable audio. */
val PREFERRED_ARCHIVE_AUDIO_FORMATS = listOf("VBR MP3", "MP3", "Ogg Vorbis", "64Kbps MP3")
