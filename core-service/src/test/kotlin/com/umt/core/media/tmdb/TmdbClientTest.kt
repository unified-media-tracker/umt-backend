package com.umt.core.media.tmdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.times
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient


class TmdbClientTest {

    private companion object {
        const val BASE_URL = "https://api.themoviedb.org/3"
    }

    private lateinit var server: MockRestServiceServer
    private lateinit var client: TmdbClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        server = MockRestServiceServer.bindTo(builder).build()
        client = TmdbClient(builder.build())
    }

    @Test
    fun `fetchMovie requests credits via append_to_response`() {
        server.expect(times(1), requestTo("$BASE_URL/movie/27205?append_to_response=credits"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": 27205, "title": "Inception", "status": "Released", "overview": "A thief",
                      "poster_path": "/poster.jpg", "release_date": "2010-07-15", "runtime": 148,
                      "genres": [{"id": 28, "name": "Action"}],
                      "credits": {
                        "crew": [
                          {"id": 525, "name": "Christopher Nolan", "job": "Director", "department": "Directing"},
                          {"id": 525, "name": "Christopher Nolan", "job": "Writer", "department": "Writing"}
                        ]
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                )
            )

        val movie = client.fetchMovie(27205)

        assertEquals("Inception", movie.title)
        assertEquals(2, movie.credits?.crew?.size)
        assertEquals("Director", movie.credits?.crew?.get(0)?.job)
        server.verify()
    }

    @Test
    fun `TmdbCredits defaults crew to an empty list when omitted`() {
        assertTrue(TmdbCredits().crew.isEmpty())
    }
}
