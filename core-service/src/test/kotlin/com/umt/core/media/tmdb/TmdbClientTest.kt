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

    private fun discoveryPage(totalPages: Int, ids: List<Long>) = """
        {"total_pages": $totalPages, "results": [${ids.joinToString(",") { """{"id": $it}""" }}]}
    """.trimIndent()

    @Test
    fun `fetchUpcomingMovieIds stops after a single page when total_pages is 1`() {
        server.expect(times(1), requestTo("$BASE_URL/movie/upcoming?region=US&page=1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(discoveryPage(totalPages = 1, ids = listOf(1, 2, 3)), MediaType.APPLICATION_JSON))

        val ids = client.fetchUpcomingMovieIds()

        assertEquals(listOf(1L, 2L, 3L), ids)
        server.verify()
    }

    @Test
    fun `fetchUpcomingMovieIds walks every page total_pages reports`() {
        server.expect(times(1), requestTo("$BASE_URL/movie/upcoming?region=US&page=1"))
            .andRespond(withSuccess(discoveryPage(totalPages = 3, ids = listOf(1)), MediaType.APPLICATION_JSON))
        server.expect(times(1), requestTo("$BASE_URL/movie/upcoming?region=US&page=2"))
            .andRespond(withSuccess(discoveryPage(totalPages = 3, ids = listOf(2)), MediaType.APPLICATION_JSON))
        server.expect(times(1), requestTo("$BASE_URL/movie/upcoming?region=US&page=3"))
            .andRespond(withSuccess(discoveryPage(totalPages = 3, ids = listOf(3)), MediaType.APPLICATION_JSON))

        val ids = client.fetchUpcomingMovieIds()

        assertEquals(listOf(1L, 2L, 3L), ids)
        server.verify()
    }

    @Test
    fun `fetchUpcomingMovieIds never requests more than MAX_PAGES even if total_pages is huge`() {
        repeat(50) { i ->
            server.expect(times(1), requestTo("$BASE_URL/movie/upcoming?region=US&page=${i + 1}"))
                .andRespond(withSuccess(discoveryPage(totalPages = 500, ids = listOf(i.toLong())), MediaType.APPLICATION_JSON))
        }

        val ids = client.fetchUpcomingMovieIds()

        assertEquals(50, ids.size)
        server.verify()
    }
}
