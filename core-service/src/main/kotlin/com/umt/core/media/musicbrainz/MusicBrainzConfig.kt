package com.umt.core.media.musicbrainz

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(MusicBrainzProperties::class)
class MusicBrainzConfig(private val properties: MusicBrainzProperties) {

    @Bean
    fun musicBrainzRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader("User-Agent", properties.userAgent)
            .build()
}
