package com.umt.core.media.igdb

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "igdb")
data class IgdbProperties(
    val clientId: String,
    val clientSecret: String,
    val apiBaseUrl: String = "https://api.igdb.com/v4",
    val authUrl: String = "https://id.twitch.tv/oauth2/token",
)
