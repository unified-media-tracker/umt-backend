package com.umt.core.media.tmdb

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate

@Component
class TmdbClient(
    private val tmdbRestClient: RestClient
) {

    fun fetchMovie(tmdbId: Long): TmdbMovieResponse =
        tmdbRestClient.get()
            .uri("/movie/{id}", tmdbId)
            .retrieve()
            .body(TmdbMovieResponse::class.java)
            ?: error("TMDB returned empty body for movie $tmdbId")

    fun fetchTvShow(tmdbId: Long): TmdbTvShowResponse =
        tmdbRestClient.get()
            .uri("/tv/{id}", tmdbId)
            .retrieve()
            .body(TmdbTvShowResponse::class.java)
            ?: error("TMDB returned empty body for tv show $tmdbId")

    // Walks every page TMDb has, from today to however far out their own data goes - not just
    // the first page. Capped defensively so a pathological response can't spin this forever;
    // TMDb's real upcoming window is nowhere close to this many pages in practice.
    fun fetchUpcomingMovieIds(region: String = "US"): List<Long> {
        val ids = mutableListOf<Long>()
        var page = 1
        var totalPages = 1

        do {
            val response = tmdbRestClient.get()
                .uri { it.path("/movie/upcoming").queryParam("region", region).queryParam("page", page).build() }
                .retrieve()
                .body(TmdbDiscoveryResponse::class.java)
                ?: break

            ids += response.results.map { it.id }
            totalPages = response.totalPages
            page++
        } while (page <= totalPages && page <= MAX_PAGES)

        return ids
    }

    fun fetchUpcomingTvShowIds(): List<Long> {
        val ids = mutableListOf<Long>()
        var page = 1
        var totalPages = 1

        do {
            val response = tmdbRestClient.get()
                .uri {
                    it.path("/discover/tv")
                        .queryParam("first_air_date.gte", LocalDate.now().toString())
                        .queryParam("sort_by", "first_air_date.asc")
                        .queryParam("page", page)
                        .build()
                }
                .retrieve()
                .body(TmdbDiscoveryResponse::class.java)
                ?: break

            ids += response.results.map { it.id }
            totalPages = response.totalPages
            page++
        } while (page <= totalPages && page <= MAX_PAGES)

        return ids
    }

    companion object {
        private const val MAX_PAGES = 50
    }
}