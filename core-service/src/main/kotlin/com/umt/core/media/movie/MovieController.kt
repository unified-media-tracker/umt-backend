package com.umt.core.media.movie

import com.umt.api.generated.MovieApi
import com.umt.api.generated.model.MediaItemResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class MovieController(
    private val movieService: MovieService,
): MovieApi {

    override fun getMovieDetails(id: UUID): ResponseEntity<MediaItemResponse> {
        return super.getMovieDetails(id)
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun importMovieFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<MediaItemResponse> =
        ResponseEntity.ok(movieService.importMovieFromTmdb(tmdbId))

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingMovies(): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(movieService.syncUpcomingMovies())

}