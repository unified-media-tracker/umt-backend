package com.umt.core.media

import com.umt.api.generated.MediaApi
import com.umt.api.generated.model.MediaCategory
import com.umt.api.generated.model.MediaItemRequest
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaSortOption
import com.umt.api.generated.model.ReleaseStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.*

@RestController
class MediaController(
    private val mediaService: MediaService,
    private val similarMediaService: SimilarMediaService,
) : MediaApi {

    override fun listMedia(
        mediaCategory: MediaCategory,
        status: ReleaseStatus?,
        sort: MediaSortOption?,
        releaseDateFrom: LocalDate?,
    ): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(mediaService.listMedia(mediaCategory, status, sort, releaseDateFrom))

    override fun getMediaById(id: UUID, mediaCategory: MediaCategory?): ResponseEntity<MediaItemResponse> =
        ResponseEntity.ok(mediaService.getMediaById(id, mediaCategory))

    override fun getSimilarMedia(id: UUID, mediaCategory: MediaCategory?): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(similarMediaService.getSimilarMedia(id, mediaCategory))

    @PreAuthorize("hasRole('USER')")
    override fun getRecommendations(@RequestBody mediaItemRequest: MediaItemRequest): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(mediaService.getUserRecommendations(userId = mediaItemRequest.userId))
}
