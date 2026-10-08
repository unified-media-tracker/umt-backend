package com.umt.core.media.movie

import com.umt.api.generated.MovieApi
import com.umt.api.generated.model.MediaItemResponse
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
    override fun importMovieFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<MediaItemResponse> =
        ResponseEntity.ok(movieImporter.importMovie(tmdbId))

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingMovies(): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(movieCatalogSyncScheduler.syncUpcomingMovies())
}
