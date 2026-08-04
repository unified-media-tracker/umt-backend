package com.umt.core.media.igdb

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.time.Instant

/**
 * IGDB itself doesn't do auth - it's fronted by Twitch's OAuth2 client-credentials flow.
 * Unlike TMDb's static API key, this token expires (Twitch's own docs show ~58 days) and
 * has to be refreshed, so it's cached here rather than baked into a RestClient default header.
 */
@Component
class IgdbTokenProvider(
    private val igdbAuthRestClient: RestClient,
    private val properties: IgdbProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var expiresAt: Instant = Instant.EPOCH

    @Synchronized
    fun getValidToken(): String {
        cachedToken?.let { if (Instant.now().isBefore(expiresAt)) return it }

        log.info("Fetching a new IGDB/Twitch access token")
        val body = LinkedMultiValueMap<String, String>().apply {
            add("client_id", properties.clientId)
            add("client_secret", properties.clientSecret)
            add("grant_type", "client_credentials")
        }

        val response = igdbAuthRestClient.post()
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .body(TwitchTokenResponse::class.java)
            ?: error("Twitch token endpoint returned an empty body")

        cachedToken = response.accessToken
        // Refresh a bit early rather than risk a request going out on an expired token.
        expiresAt = Instant.now().plusSeconds(response.expiresIn - REFRESH_MARGIN_SECONDS)
        return response.accessToken
    }

    companion object {
        private const val REFRESH_MARGIN_SECONDS = 300L
    }
}
