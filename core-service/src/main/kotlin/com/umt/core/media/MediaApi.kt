package com.umt.core.media

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/api/core/media")
interface MediaApi {

    @PostMapping("/import/tmdb/{tmdbId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun importFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<MediaItem>
}