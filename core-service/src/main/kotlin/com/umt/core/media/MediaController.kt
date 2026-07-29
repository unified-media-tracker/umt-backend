package com.umt.core.media

import com.umt.core.media.dto.request.MediaItemRequest
import com.umt.core.media.dto.response.MediaItemResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MediaController(private val mediaService: MediaService) : MediaApi {

    override fun importFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<MediaItemResponse> =
        ResponseEntity.ok(mediaService.importMovieFromTmdb(tmdbId))

    override fun getRecommendations(@RequestBody request: MediaItemRequest): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(mediaService.getUserRecommendations(userId = request.userId))

}