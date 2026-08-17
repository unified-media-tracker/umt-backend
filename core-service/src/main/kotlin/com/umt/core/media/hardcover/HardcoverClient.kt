package com.umt.core.media.hardcover

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate

@Component
class HardcoverClient(
    private val hardcoverRestClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun fetchUpcomingBooks(pageSize: Int = PAGE_SIZE): List<HardcoverBook> {
        val all = mutableListOf<HardcoverBook>()
        var offset = 0
        var pageCount = 0

        while (true) {
            val query = """
                query UpcomingBooks {
                  books(
                    where: {release_date: {_gt: "${LocalDate.now()}"}}
                    order_by: {release_date: asc}
                    limit: $pageSize
                    offset: $offset
                  ) {
                    id
                    title
                    description
                    slug
                    releaseDate: release_date
                    image { url }
                    contributions {
                      author { id name }
                      contribution
                    }
                  }
                }
            """.trimIndent()

            val response = hardcoverRestClient.post()
                .body(mapOf("query" to query))
                .retrieve()
                .body(HardcoverGraphQlResponse::class.java)

            val page = response?.data?.books ?: emptyList()
            all += page
            pageCount++
            if (page.size < pageSize || pageCount >= MAX_PAGES) break
            offset += pageSize
        }

        log.info("Fetched {} upcoming books from Hardcover across {} page(s)", all.size, pageCount)
        return all
    }

    companion object {
        private const val PAGE_SIZE = 200
        private const val MAX_PAGES = 50
    }
}
