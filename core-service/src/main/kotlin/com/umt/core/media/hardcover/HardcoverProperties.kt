package com.umt.core.media.hardcover

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "hardcover")
data class HardcoverProperties(
    val apiToken: String,
    val apiUrl: String = "https://api.hardcover.app/v1/graphql",
)
