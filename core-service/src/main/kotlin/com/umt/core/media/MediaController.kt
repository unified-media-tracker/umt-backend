package com.umt.core.media

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class MediaController(private val mediaService: MediaService) : MediaApi {

    override fun importFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<MediaItem> =
        ResponseEntity.ok(mediaService.importMovieFromTmdb(tmdbId))
}