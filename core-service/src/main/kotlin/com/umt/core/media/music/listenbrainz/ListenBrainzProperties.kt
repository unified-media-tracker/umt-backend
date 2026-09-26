package com.umt.core.media.music.listenbrainz

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "listenbrainz")
data class ListenBrainzProperties(
    val baseUrl: String,
    val userAgent: String,
    val similarArtistsAlgorithm: String,
)
