package com.umt.core.media.igdb

import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant

@Component
class IgdbClient(
    private val igdbApiRestClient: RestClient,
    private val properties: IgdbProperties,
    private val tokenProvider: IgdbTokenProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Games with a known future first_release_date - IGDB has no dedicated "upcoming" endpoint;
     * this is the same shape as TMDb's /movie/upcoming, just expressed as a filter instead of a
     * named route.
     *
     * Category = 0 means "main game" (excludes DLC/ports/remasters/etc), but IGDB omits the
     * field entirely for a lot of untagged entries — mostly smaller/indie titles, which happen
     * to dominate the near-term upcoming list. A strict `category = 0` filter was silently
     * excluding almost everything for that reason (verified live: dropped ~20 real upcoming
     * games down to 0). `category = null` Lets untagged entries back in without giving up the
     * DLC/port exclusion for anything that IS explicitly tagged as such.
     */
    // Pages through offset until IGDB gives back fewer than a full page - i.e. the actual end
    // of their upcoming-games data - rather than stopping at one arbitrary batch size. Capped
    // defensively (MAX_PAGES) against a pathological response looping forever.
    fun fetchUpcomingGames(pageSize: Int = PAGE_SIZE): List<IgdbGame> {
        val all = mutableListOf<IgdbGame>()
        val now = Instant.now().epochSecond
        var offset = 0
        var pageCount = 0

        while (true) {
            val query = """
                fields name, first_release_date, summary, cover.image_id;
                where first_release_date > $now & (category = 0 | category = null);
                sort first_release_date asc;
                limit $pageSize;
                offset $offset;
            """.trimIndent()

            val page = igdbApiRestClient.post()
                .uri("/games")
                .header("Client-ID", properties.clientId)
                .header("Authorization", "Bearer ${tokenProvider.getValidToken()}")
                .contentType(MediaType.TEXT_PLAIN)
                .body(query)
                .retrieve()
                .body(object : ParameterizedTypeReference<List<IgdbGame>>() {})
                ?: emptyList()

            all += page
            pageCount++
            if (page.size < pageSize || pageCount >= MAX_PAGES) break
            offset += pageSize
        }

        log.info("Fetched {} upcoming games from IGDB across {} page(s)", all.size, pageCount)
        return all
    }

    companion object {
        private const val PAGE_SIZE = 200
        private const val MAX_PAGES = 50
    }
}
