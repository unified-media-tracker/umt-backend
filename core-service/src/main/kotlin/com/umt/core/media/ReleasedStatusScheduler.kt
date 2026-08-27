package com.umt.core.media

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReleasedStatusScheduler(
    private val releaseDateSyncService: ReleaseDateSyncService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 45 5 * * *")
    fun checkForNewlyReleasedItems() {
        log.info("Starting scheduled released-status sweep")
        releaseDateSyncService.checkForNewlyReleasedItems()
    }
}
