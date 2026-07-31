package com.umt.core.media.metacritic

import com.umt.core.media.MediaService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Proactive catalogue sync — runs on its own schedule, independent of any user ever
 * searching for these albums, so the release board/calendar has them ahead of time
 * instead of only importing reactively on the first search.
 */
@Component
class AlbumCatalogSyncScheduler(
    private val mediaService: MediaService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *")
    fun syncUpcomingAlbums() {
        log.info("Starting scheduled upcoming-albums sync")
        mediaService.syncUpcomingAlbums()
    }
}
