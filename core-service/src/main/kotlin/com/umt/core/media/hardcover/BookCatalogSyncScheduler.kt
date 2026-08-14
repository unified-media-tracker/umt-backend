package com.umt.core.media.hardcover

import com.umt.core.media.MediaService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BookCatalogSyncScheduler(
    private val mediaService: MediaService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 5 * * *")
    fun syncUpcomingBooks() {
        log.info("Starting scheduled upcoming-books sync")
        mediaService.syncUpcomingBooks()
    }
}
