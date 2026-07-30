package com.umt.core.media

import com.umt.api.generated.MediaApi
import com.umt.api.generated.model.MediaItemRequest
import com.umt.api.generated.model.MediaItemResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody

class MediaController(private val mediaService: MediaService) : MediaApi {

    @PreAuthorize("hasRole('ADMIN')")
    override fun importFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<MediaItemResponse> =
        ResponseEntity.ok(mediaService.importMovieFromTmdb(tmdbId))

    @PreAuthorize("hasRole('USER')")
    override fun getRecommendations(@RequestBody mediaItemRequest: MediaItemRequest): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(mediaService.getUserRecommendations(userId = mediaItemRequest.userId))

}