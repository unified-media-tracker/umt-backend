package com.umt.core.media.tmdb

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

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
}