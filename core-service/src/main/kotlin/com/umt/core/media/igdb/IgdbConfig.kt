package com.umt.core.media.igdb

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(IgdbProperties::class)
class IgdbConfig(private val properties: IgdbProperties) {

    @Bean
    fun igdbAuthRestClient(): RestClient =
        RestClient.builder().baseUrl(properties.authUrl).build()

    @Bean
    fun igdbApiRestClient(): RestClient =
        RestClient.builder().baseUrl(properties.apiBaseUrl).build()
}
