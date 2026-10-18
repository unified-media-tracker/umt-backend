package com.umt.core.media.tvshow

import com.umt.api.generated.model.TvShowResponse
import com.umt.core.media.movie.tmdb.TmdbClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TvShowCatalogSyncScheduler(
    private val tmdbClient: TmdbClient,
    private val tvShowImporter: TvShowImporter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 15 5 * * *")
    fun syncUpcomingTvSeries(): List<TvShowResponse> {
        log.info("Starting scheduled upcoming-tv-series sync")
        val ids = tmdbClient.fetchUpcomingTvShowIds()
        val results = mutableListOf<TvShowResponse>()

        for (id in ids) {
            try {
                results.add(tvShowImporter.importTvShow(id))
            } catch (ex: Exception) {
                log.error("Failed to import upcoming tv show tmdbId={}, skipping it this run", id, ex)
            }
        }

        log.info("TV sync: {} discovered from TMDb", ids.size)
        return results
    }
}
