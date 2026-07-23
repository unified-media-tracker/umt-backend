package com.umt.core.media.tmdb

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(TmdbProperties::class)
class TmdbConfig(private val properties: TmdbProperties) {

    @Bean
    fun tmdbRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader("Authorization", "Bearer ${properties.apiKey}")
            .build()
}