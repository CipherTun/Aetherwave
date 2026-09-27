package com.ciphertun.aetherwave.model

/** A podcast show, resolved from Apple's iTunes catalogue down to a playable RSS feed. */
data class PodcastShow(
    val id: String,
    val title: String,
    val author: String?,
    val artworkUrl: String?,
    val feedUrl: String,
    /** ISO 3166-1 alpha-2 storefront this result came from, e.g. "US", "JP", "BR". */
    val country: String?
)

/** A small, easy-to-extend starter set for a country-picker chip row. Apple supports 175+. */
val FEATURED_COUNTRIES = listOf(
    "US" to "United States",
    "ZA" to "South Africa",
    "NG" to "Nigeria",
    "GB" to "United Kingdom",
    "DE" to "Germany",
    "FR" to "France",
    "BR" to "Brazil",
    "IN" to "India",
    "JP" to "Japan",
    "AU" to "Australia"
)
