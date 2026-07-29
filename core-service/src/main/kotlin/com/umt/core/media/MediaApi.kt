package com.umt.core.media

import com.umt.core.media.dto.request.MediaItemRequest
import com.umt.core.media.dto.response.MediaItemResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Tag(name = "Media", description = "Operations related to media items (movies, games, etc.)")
@RequestMapping("/api/core/media")
interface MediaApi {

    @Operation(summary = "Import movie from TMDB", description = "Imports a movie from TMDB by its ID. Requires ADMIN role.")
    @PostMapping("/import/tmdb/{tmdbId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun importFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<MediaItemResponse>

    @Operation(summary = "Get recommended media content", description = "Returns a list of recommended movies based on " +
            "user preferences (todo - make it work not as a mock hueta).")
    @PostMapping("/recommendations")
    @PreAuthorize("hasRole('USER')")
    fun getRecommendations(@RequestBody request: MediaItemRequest): ResponseEntity<List<MediaItemResponse>>

}