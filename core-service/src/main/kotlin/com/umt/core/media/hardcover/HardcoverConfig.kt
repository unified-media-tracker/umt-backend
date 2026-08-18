package com.umt.core.media.hardcover

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(HardcoverProperties::class)
class HardcoverConfig(private val properties: HardcoverProperties) {

    @Bean
    fun hardcoverRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(properties.apiUrl)
            .defaultHeader("Authorization", "Bearer ${properties.apiToken}")
            .defaultHeader("Content-Type", "application/json")
            .build()
}
