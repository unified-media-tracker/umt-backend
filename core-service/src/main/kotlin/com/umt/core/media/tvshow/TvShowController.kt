package com.umt.core.media.tvshow

import com.umt.api.generated.TvShowApi
import com.umt.api.generated.model.TvShowResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class TvShowController(
    private val tvShowImporter: TvShowImporter,
    private val tvShowCatalogSyncScheduler: TvShowCatalogSyncScheduler,
) : TvShowApi {

    @PreAuthorize("hasRole('ADMIN')")
    override fun importTvShowFromTmdb(@PathVariable tmdbId: Long): ResponseEntity<TvShowResponse> =
        ResponseEntity.ok(tvShowImporter.importTvShow(tmdbId))

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingTvSeries(): ResponseEntity<List<TvShowResponse>> =
        ResponseEntity.ok(tvShowCatalogSyncScheduler.syncUpcomingTvSeries())
}
