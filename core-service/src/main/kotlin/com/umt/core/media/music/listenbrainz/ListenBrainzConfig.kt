package com.umt.core.media.music.listenbrainz

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(ListenBrainzProperties::class)
class ListenBrainzConfig(private val properties: ListenBrainzProperties) {

    // A GET request waits on this call, so a stalled ListenBrainz must not stall it too.
    @Bean
    fun listenBrainzRestClient(): RestClient {
        val requestFactory = JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build())
            .apply { setReadTimeout(READ_TIMEOUT) }

        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader("User-Agent", properties.userAgent)
            .requestFactory(requestFactory)
            .build()
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
