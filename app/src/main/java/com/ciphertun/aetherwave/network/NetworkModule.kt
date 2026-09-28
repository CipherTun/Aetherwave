package com.ciphertun.aetherwave.network

import com.ciphertun.aetherwave.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Plain manual DI — no Hilt/Koin, kept simple since this is a small,
 * single-module app. Swap for a DI framework later if the app grows.
 */
object NetworkModule {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonConverter = json.asConverterFactory("application/json".toMediaType())

    private fun baseClient(extra: (OkHttpClient.Builder) -> Unit = {}): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .apply(extra)
            .build()
    }

    val jamendoApi: JamendoApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://127.0.0.1:17843/api/jamendo/")
            .client(baseClient())
            .addConverterFactory(jsonConverter)
            .build()
            .create(JamendoApi::class.java)
    }

    val archiveApi: ArchiveApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://archive.org/")
            .client(baseClient())
            .addConverterFactory(jsonConverter)
            .build()
            .create(ArchiveApi::class.java)
    }

    val itunesApi: ItunesPodcastApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(baseClient())
            .addConverterFactory(jsonConverter)
            .build()
            .create(ItunesPodcastApi::class.java)
    }

    val appleChartsApi: AppleChartsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://rss.marketingtools.apple.com/api/v2/")
            .client(baseClient())
            .addConverterFactory(jsonConverter)
            .build()
            .create(AppleChartsApi::class.java)
    }

    val openverseApi: OpenverseApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openverse.org/")
            .client(baseClient())
            .addConverterFactory(jsonConverter)
            .build()
            .create(OpenverseApi::class.java)
    }

    /** Plain client (no JSON converter) for fetching raw RSS feed bodies to hand to RssFeedParser. */
    val rssHttpClient: OkHttpClient by lazy { baseClient() }
}
