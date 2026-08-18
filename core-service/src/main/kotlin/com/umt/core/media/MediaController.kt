package com.umt.core.media

import com.umt.api.generated.MediaApi
import com.umt.api.generated.model.MediaItemRequest
import com.umt.api.generated.model.MediaItemResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MediaController(private val mediaService: MediaService) : MediaApi {

    @PreAuthorize("hasRole('ADMIN')")
    override fun importMovieFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<MediaItemResponse> =
        ResponseEntity.ok(mediaService.importMovieFromTmdb(tmdbId))

    @PreAuthorize("hasRole('ADMIN')")
    override fun importTvShowFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<MediaItemResponse> =
        ResponseEntity.ok(mediaService.importTvShowFromTmdb(tmdbId))

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingMovies(): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(mediaService.syncUpcomingMovies())

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingTvSeries(): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(mediaService.syncUpcomingTvSeries())

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingAlbums(): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(mediaService.syncUpcomingAlbums())

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingGames(): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(mediaService.syncUpcomingGames())

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingBooks(): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(mediaService.syncUpcomingBooks())

    @PreAuthorize("hasRole('USER')")
    override fun getRecommendations(@RequestBody mediaItemRequest: MediaItemRequest): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(mediaService.getUserRecommendations(userId = mediaItemRequest.userId))

}