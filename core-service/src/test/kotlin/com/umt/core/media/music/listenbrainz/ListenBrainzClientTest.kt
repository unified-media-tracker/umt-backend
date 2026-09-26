package com.umt.core.media.music.listenbrainz

import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

class ListenBrainzClientTest {

    private companion object {
        const val BASE_URL = "https://labs.api.listenbrainz.org"
        const val ALGORITHM = "session_based_days_9000_session_300_contribution_5_threshold_15_limit_50_skip_30"
        const val ARTIST = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
    }

    private lateinit var server: MockRestServiceServer
    private lateinit var client: ListenBrainzClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        server = MockRestServiceServer.bindTo(builder).build()
        client = ListenBrainzClient(builder.build(), ListenBrainzProperties(BASE_URL, "UMT-test", ALGORITHM))
    }

    private fun expectLookup() =
        server.expect(requestTo(startsWith("$BASE_URL/similar-artists/json")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("artist_mbids", ARTIST))
            .andExpect(queryParam("algorithm", ALGORITHM))

    @Test
    fun `reads the similar artists in the order ListenBrainz ranks them`() {
        expectLookup().andRespond(
            withSuccess(
                """
                [{"artist_mbid": "5b11f4ce-a62d-471e-81fc-a69a8278c7da", "name": "Nirvana", "comment": "1980s-1990s US grunge band",
                  "type": "Group", "gender": null, "score": 11782, "reference_mbid": "$ARTIST"},
                 {"artist_mbid": "9c9f1380-2516-4fc9-a3e6-f9f61941d090", "name": "Muse", "comment": "UK rock band",
                  "type": "Group", "gender": null, "score": 10817, "reference_mbid": "$ARTIST"}]
                """.trimIndent(),
                MediaType.APPLICATION_JSON,
            )
        )

        val similar = client.fetchSimilarArtists(ARTIST)

        assertEquals(
            listOf(
                ListenBrainzSimilarArtist("5b11f4ce-a62d-471e-81fc-a69a8278c7da", "Nirvana", 11782),
                ListenBrainzSimilarArtist("9c9f1380-2516-4fc9-a3e6-f9f61941d090", "Muse", 10817),
            ),
            similar,
        )
        server.verify()
    }

    @Test
    fun `an artist ListenBrainz has no listening data for yields an empty list`() {
        expectLookup().andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        assertTrue(client.fetchSimilarArtists(ARTIST).isEmpty())
        server.verify()
    }

    @Test
    fun `an empty body yields an empty list`() {
        expectLookup().andRespond(withNoContent())

        assertTrue(client.fetchSimilarArtists(ARTIST).isEmpty())
        server.verify()
    }

    @Test
    fun `a rejected request propagates, since retrying it cannot help`() {
        expectLookup().andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_HTML).body("<h1>Bad Request</h1>"))

        assertThrows(HttpClientErrorException::class.java) { client.fetchSimilarArtists(ARTIST) }
        server.verify()
    }
}
