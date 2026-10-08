package com.umt.core.media.music

import com.umt.api.generated.MusicApi
import com.umt.api.generated.model.MediaItemResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class MusicController(
    private val musicCatalogSyncScheduler: MusicCatalogSyncScheduler,
) : MusicApi {

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingMusic(): ResponseEntity<List<MediaItemResponse>> =
        ResponseEntity.ok(musicCatalogSyncScheduler.syncUpcomingMusic())
}
