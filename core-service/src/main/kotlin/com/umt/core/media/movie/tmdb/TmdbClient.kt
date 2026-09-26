package com.umt.core.media.movie.tmdb

import com.umt.core.media.tvshow.tmdb.TmdbTvShowResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate

@Component
class TmdbClient(
    private val tmdbRestClient: RestClient
) {

    // append_to_response bundles crew (for director/writer credits) into the same request -
    // no extra API call or added cost, TMDb explicitly supports this for exactly this reason.
    fun fetchMovie(tmdbId: Long): TmdbMovieResponse =
        tmdbRestClient.get()
            .uri { it.path("/movie/{id}").queryParam("append_to_response", "credits").build(tmdbId) }
            .retrieve()
            .body(TmdbMovieResponse::class.java)
            ?: error("TMDB returned empty body for movie $tmdbId")

    fun fetchTvShow(tmdbId: Long): TmdbTvShowResponse =
        tmdbRestClient.get()
            .uri("/tv/{id}", tmdbId)
            .retrieve()
            .body(TmdbTvShowResponse::class.java)
            ?: error("TMDB returned empty body for tv show $tmdbId")

    // Same {total_pages, results: [{id}]} shape as the upcoming/discover endpoints below -
    // TmdbDiscoveryResponse already fits, no separate DTO needed. Only page 1 (up to 20 results,
    // TMDb's own page size) - plenty for a "more like this" strip, and the caller filters this
    // down further to whatever's already in our own catalog anyway.
    fun fetchMovieRecommendationIds(tmdbId: Long): List<Long> =
        tmdbRestClient.get()
            .uri("/movie/{id}/recommendations", tmdbId)
            .retrieve()
            .body(TmdbDiscoveryResponse::class.java)
            ?.results?.map { it.id }
            ?: emptyList()

    fun fetchTvRecommendationIds(tmdbId: Long): List<Long> =
        tmdbRestClient.get()
            .uri("/tv/{id}/recommendations", tmdbId)
            .retrieve()
            .body(TmdbDiscoveryResponse::class.java)
            ?.results?.map { it.id }
            ?: emptyList()

    // Walks every page TMDb has, from today to however far out their own data goes - not just
    // the first page. Capped defensively so a pathological response can't spin this forever;
    // TMDb's real upcoming window is nowhere close to this many pages in practice.
    fun fetchUpcomingMovieIds(region: String = "US"): List<Long> {
        val ids = mutableListOf<Long>()
        var page = 1

        while (page <= MAX_PAGES) {
            val response = tmdbRestClient.get()
                .uri { it.path("/movie/upcoming").queryParam("region", region).queryParam("page", page).build() }
                .retrieve()
                .body(TmdbDiscoveryResponse::class.java)
                ?: break

            ids += response.results.map { it.id }
            if (page >= response.totalPages) break
            page++
        }

        return ids
    }

    fun fetchUpcomingTvShowIds(): List<Long> {
        val ids = mutableListOf<Long>()
        var page = 1

        while (page <= MAX_PAGES) {
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
            if (page >= response.totalPages) break
            page++
        }

        return ids
    }

    companion object {
        private const val MAX_PAGES = 50
    }
}