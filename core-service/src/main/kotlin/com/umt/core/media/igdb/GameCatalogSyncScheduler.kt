package com.umt.core.media.igdb

import com.umt.core.media.MediaService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class GameCatalogSyncScheduler(
    private val mediaService: MediaService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 4 * * *")
    fun syncUpcomingGames() {
        log.info("Starting scheduled upcoming-games sync")
        mediaService.syncUpcomingGames()
    }
}
