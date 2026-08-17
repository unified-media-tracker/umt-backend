package com.umt.core.media

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

/**
 * Called on every re-sync of an already-known item. If the source's date moved, updates the
 * record and logs it to release_status_history so the UI can show "delayed from X to Y" —
 * ai-analyser isn't involved here at all: it only reacts to 'media.imported' (new items), it has
 * no recurring schedule of its own, so it would never notice a date change by itself.
 *
 * Its own bean (not a private method on MediaServiceImpl), so TmdbCatalogImporter can call it
 * too without a circular dependency back into MediaServiceImpl.
 */
@Component
class ReleaseDateSyncService(
    private val mediaItemRepository: MediaRepository,
    private val releaseStatusHistoryRepository: ReleaseStatusHistoryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun updateIfChanged(existing: MediaItem, incomingDate: LocalDate?, sourceLabel: String): MediaItem {
        if (incomingDate == null) return existing

        val previousDate = existing.releaseDate
        if (previousDate != null && previousDate.isEqual(incomingDate)) return existing

        existing.releaseDate = incomingDate

        if (existing.releaseDateStatus != ReleaseStatus.RELEASED && previousDate != null && incomingDate.isAfter(previousDate)) {
            existing.releaseDateStatus = ReleaseStatus.DELAYED
        }

        val saved = mediaItemRepository.save(existing)
        val note = "$sourceLabel: release date moved from ${previousDate ?: "unset"} to $incomingDate"
        log.info("Release date change detected for '{}': {}", saved.title, note)

        releaseStatusHistoryRepository.save(
            ReleaseStatusHistory(
                mediaItem = saved,
                status = saved.releaseDateStatus,
                changedAt = Instant.now(),
                sourceNote = note,
            )
        )

        return saved
    }
}
