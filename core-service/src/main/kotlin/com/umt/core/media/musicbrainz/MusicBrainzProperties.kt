package com.umt.core.media.musicbrainz

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "musicbrainz")
data class MusicBrainzProperties(
    val baseUrl: String,
    val userAgent: String,
)
