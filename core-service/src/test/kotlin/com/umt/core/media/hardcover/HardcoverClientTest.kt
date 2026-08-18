package com.umt.core.media.hardcover

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
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * Pagination is the only real logic here — everything else is a straight pass-through to
 * Jackson. Driven entirely against a fake HTTP server, so no real Hardcover credentials or
 * network access is required.
 */
class HardcoverClientTest {

    private companion object {
        const val BASE_URL = "https://api.hardcover.app/v1/graphql"
    }

    private lateinit var server: MockRestServiceServer
    private lateinit var client: HardcoverClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        server = MockRestServiceServer.bindTo(builder).build()
        client = HardcoverClient(builder.build())
    }

    private fun bookJson(id: Long) = """
        {"id": $id, "title": "Book $id", "description": null, "slug": "book-$id",
         "releaseDate": "2027-01-0${(id % 9) + 1}", "image": null, "contributions": []}
    """.trimIndent()

    private fun pageResponse(books: List<Long>) =
        """{"data": {"books": [${books.joinToString(",") { bookJson(it) }}]}}"""

    private fun expectPage(response: String) {
        server.expect(times(1), requestTo(BASE_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `stops after a single page shorter than the page size`() {
        expectPage(pageResponse(listOf(1, 2, 3)))

        val books = client.fetchUpcomingBooks(pageSize = 200)

        assertEquals(3, books.size)
        assertEquals(listOf(1L, 2L, 3L), books.map { it.id })
        server.verify()
    }

    @Test
    fun `follows pagination across multiple full pages`() {
        expectPage(pageResponse(listOf(1, 2)))
        expectPage(pageResponse(listOf(3)))

        val books = client.fetchUpcomingBooks(pageSize = 2)

        assertEquals(3, books.size)
        assertEquals(listOf(1L, 2L, 3L), books.map { it.id })
        server.verify()
    }

    @Test
    fun `stops after MAX_PAGES even if every page was full`() {
        repeat(50) { expectPage(pageResponse(listOf(1))) }

        val books = client.fetchUpcomingBooks(pageSize = 1)

        assertEquals(50, books.size)
        server.verify()
    }

    @Test
    fun `an empty body for the first page yields no books`() {
        server.expect(times(1), requestTo(BASE_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withNoContent())

        val books = client.fetchUpcomingBooks(pageSize = 200)

        assertTrue(books.isEmpty())
        server.verify()
    }

    @Test
    fun `a response with a null data field yields no books`() {
        expectPage("""{"data": null}""")

        val books = client.fetchUpcomingBooks(pageSize = 200)

        assertTrue(books.isEmpty())
        server.verify()
    }
}
