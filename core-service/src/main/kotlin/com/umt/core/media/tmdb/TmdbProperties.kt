package com.umt.core.media.tmdb

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tmdb")
data class TmdbProperties(
    val baseUrl: String,
    val apiKey: String,
)