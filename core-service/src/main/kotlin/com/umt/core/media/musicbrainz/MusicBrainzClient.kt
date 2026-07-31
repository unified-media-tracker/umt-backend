package com.umt.core.media.musicbrainz

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

@Component
class MusicBrainzClient(
    private val musicBrainzRestClient: RestClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var lastRequestAt = 0L

    /**
     * Best release-group match for an artist/title pair, or null if nothing
     * scored above MIN_CONFIDENCE_SCORE — a wrong low-confidence match would
     * wire the wrong catalogue identity to a real release, which is worse than
     * just not importing it yet.
     *
     * MusicBrainz's own server (community-run, not a paid tier) occasionally answers
     * 503 "busy, try again later" under load — that's not our bug, so this retries a
     * few times with backoff before giving up. A permanent give up still isn't a loss:
     * the candidate hasn't persisted, so the next day's scheduled sync just tries again.
     */
    @Synchronized
    fun findReleaseGroup(artist: String, title: String): MusicBrainzReleaseGroup? {
        for (attempt in 1..MAX_ATTEMPTS) {
            throttle()

            val response = try {
                val query = """artist:"$artist" AND releasegroup:"$title""""
                musicBrainzRestClient.get()
                    .uri {
                        it.path("/release-group/")
                            .queryParam("query", query)
                            .queryParam("fmt", "json")
                            .queryParam("limit", 3)
                            .build()
                    }
                    .retrieve()
                    .body(MusicBrainzSearchResponse::class.java)
            } catch (ex: HttpServerErrorException) {
                log.warn(
                    "MusicBrainz {} for {} — {} (attempt {}/{})",
                    ex.statusCode, artist, title, attempt, MAX_ATTEMPTS,
                )
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Giving up on {} — {} after {} attempts; next scheduled sync will retry", artist, title, MAX_ATTEMPTS)
                    return null
                }
                Thread.sleep(RETRY_BACKOFF_MS * attempt)
                continue
            }

            val best = response?.releaseGroups?.firstOrNull()
            if (best == null) {
                log.info("No MusicBrainz match for {} — {}", artist, title)
                return null
            }
            if (best.score < MIN_CONFIDENCE_SCORE) {
                log.info("Best MusicBrainz match for {} — {} scored only {}, discarding", artist, title, best.score)
                return null
            }
            return best
        }
        return null
    }

    // MusicBrainz enforces ~1 request/second for unauthenticated use — do not lower this
    // without a registered application and a higher-tier quota.
    private fun throttle() {
        val elapsed = System.currentTimeMillis() - lastRequestAt
        if (elapsed < MIN_INTERVAL_MS) Thread.sleep(MIN_INTERVAL_MS - elapsed)
        lastRequestAt = System.currentTimeMillis()
    }

    companion object {
        private const val MIN_INTERVAL_MS = 1100L
        private const val MIN_CONFIDENCE_SCORE = 80
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BACKOFF_MS = 4000L
    }
}
