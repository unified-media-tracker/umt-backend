package com.umt.core.media.movie

import com.umt.api.generated.MovieApi
import com.umt.api.generated.model.MovieResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class MovieController(
    private val movieImporter: MovieImporter,
    private val movieCatalogSyncScheduler: MovieCatalogSyncScheduler,
) : MovieApi {

    @PreAuthorize("hasRole('ADMIN')")
    override fun importMovieFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<MovieResponse> =
        ResponseEntity.ok(movieImporter.importMovie(tmdbId))

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingMovies(): ResponseEntity<List<MovieResponse>> =
        ResponseEntity.ok(movieCatalogSyncScheduler.syncUpcomingMovies())
}
