package com.umt.core.media.igdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.times
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * Twitch tokens are long-lived but not permanent, so the provider caches one and refreshes it
 * early. The refresh margin is the interesting part: a token that expires inside the margin has
 * to be treated as already stale, otherwise a request can go out on a token that dies in flight.
 *
 * Rather than mock the clock, these tests drive the margin from the server side — an expires_in
 * shorter than REFRESH_MARGIN_SECONDS puts the cached expiry in the past by construction.
 */
class IgdbTokenProviderTest {

    private companion object {
        const val AUTH_URL = "https://id.twitch.tv/oauth2/token"
        const val CLIENT_ID = "test-client-id"
        const val CLIENT_SECRET = "test-client-secret"
    }

    private lateinit var server: MockRestServiceServer
    private lateinit var provider: IgdbTokenProvider

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(AUTH_URL)
        server = MockRestServiceServer.bindTo(builder).build()
        provider = IgdbTokenProvider(
            igdbAuthRestClient = builder.build(),
            properties = IgdbProperties(clientId = CLIENT_ID, clientSecret = CLIENT_SECRET),
        )
    }

    private fun expectTokenRequest(count: Int, accessToken: String, expiresIn: Long) {
        server.expect(times(count), requestTo(AUTH_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"access_token":"$accessToken","expires_in":$expiresIn,"token_type":"bearer"}""",
                    MediaType.APPLICATION_JSON,
                )
            )
    }

    @Test
    fun `fetches a token on first use`() {
        expectTokenRequest(count = 1, accessToken = "token-abc", expiresIn = 5_000_000)

        assertEquals("token-abc", provider.getValidToken())

        server.verify()
    }

    @Test
    fun `sends the client credentials grant as form-encoded parameters`() {
        server.expect(times(1), requestTo(AUTH_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().formData(
                org.springframework.util.LinkedMultiValueMap<String, String>().apply {
                    add("client_id", CLIENT_ID)
                    add("client_secret", CLIENT_SECRET)
                    add("grant_type", "client_credentials")
                }
            ))
            .andRespond(
                withSuccess(
                    """{"access_token":"token-abc","expires_in":5000000}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        provider.getValidToken()

        server.verify()
    }

    @Test
    fun `reuses the cached token instead of hitting Twitch again`() {
        // exactly one request is expected — a second call to Twitch would fail verify()
        expectTokenRequest(count = 1, accessToken = "token-abc", expiresIn = 5_000_000)

        val first = provider.getValidToken()
        val second = provider.getValidToken()

        assertEquals("token-abc", first)
        assertEquals(first, second)
        server.verify()
    }

    @Test
    fun `refreshes a token that expires inside the refresh margin`() {
        // expires_in of 100 s is shorter than the 300 s margin, so the cached expiry is already
        // in the past and the very next call must re-fetch
        expectTokenRequest(count = 2, accessToken = "token-short", expiresIn = 100)

        provider.getValidToken()
        provider.getValidToken()

        server.verify()
    }

    @Test
    fun `fails loudly when Twitch returns an empty body`() {
        server.expect(times(1), requestTo(AUTH_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withNoContent())

        val error = assertThrows(IllegalStateException::class.java) { provider.getValidToken() }

        assertEquals("Twitch token endpoint returned an empty body", error.message)
    }
}
