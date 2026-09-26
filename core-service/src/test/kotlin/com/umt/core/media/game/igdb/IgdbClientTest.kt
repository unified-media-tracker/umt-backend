package com.umt.core.media.game.igdb

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.times
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * The OAuth token flow itself belongs to IgdbTokenProviderTest - here the provider is just
 * mocked to hand back a fixed token, so these can focus on the actual /games query.
 */
class IgdbClientTest {

    private companion object {
        const val BASE_URL = "https://api.igdb.com/v4"
    }

    private lateinit var server: MockRestServiceServer
    private lateinit var tokenProvider: IgdbTokenProvider
    private lateinit var client: IgdbClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        server = MockRestServiceServer.bindTo(builder).build()
        tokenProvider = mockk()
        every { tokenProvider.getValidToken() } returns "test-token"
        client = IgdbClient(builder.build(), IgdbProperties(clientId = "test-client-id", clientSecret = "test-client-secret"), tokenProvider)
    }

    @Test
    fun `fetchSimilarGameIds sends the game's own id as a where filter`() {
        server.expect(times(1), requestTo("$BASE_URL/games"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Client-ID", "test-client-id"))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andExpect(content().string("fields similar_games; where id = 1942;"))
            .andRespond(
                withSuccess(
                    """[{"id": 1942, "name": "The Witcher 3: Wild Hunt", "similar_games": [119133, 1887, 478]}]""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val ids = client.fetchSimilarGameIds(1942)

        assertEquals(listOf(119133L, 1887L, 478L), ids)
        server.verify()
    }

    @Test
    fun `fetchSimilarGameIds returns an empty list when the game has none`() {
        server.expect(times(1), requestTo("$BASE_URL/games"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""[{"id": 1, "name": "Obscure Game"}]""", MediaType.APPLICATION_JSON))

        val ids = client.fetchSimilarGameIds(1)

        assertTrue(ids.isEmpty())
        server.verify()
    }

    @Test
    fun `fetchSimilarGameIds returns an empty list for an unknown game id`() {
        server.expect(times(1), requestTo("$BASE_URL/games"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        val ids = client.fetchSimilarGameIds(999_999)

        assertTrue(ids.isEmpty())
        server.verify()
    }
}
