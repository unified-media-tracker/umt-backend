package com.umt.core.media.music

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.music.metacritic.MetacriticMusicClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Proactive catalogue sync — runs on its own schedule, independent of any user ever searching
 * for these releases, so the release board/calendar has them ahead of time instead of only
 * importing reactively on the first search.
 */
@Component
class MusicCatalogSyncScheduler(
    private val metacriticMusicClient: MetacriticMusicClient,
    private val musicImporter: MusicImporter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Deliberately not @Transactional: MusicBrainz enforces ~1 request/second, so this loop
    // can run for a while on a big batch. Each importCandidate() call is already transactional
    // on its own - one bad candidate shouldn't roll back releases already imported earlier in
    // the same run.
    @Scheduled(cron = "0 0 4 * * *")
    fun syncUpcomingMusic(): List<MediaItemResponse> {
        log.info("Starting scheduled upcoming-music sync")
        val discovered = metacriticMusicClient.fetchUpcomingMusic()
        val results = mutableListOf<MediaItemResponse>()

        for (candidate in discovered) {
            try {
                musicImporter.importCandidate(candidate)?.let { results.add(it) }
            } catch (ex: Exception) {
                log.error("Failed to process candidate {} - {}, skipping it this run", candidate.artist, candidate.title, ex)
            }
        }

        log.info("Music sync: {} discovered from Metacritic", discovered.size)
        return results
    }
}
