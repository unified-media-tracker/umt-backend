package com.umt.core.media.movie

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.movie.tmdb.TmdbClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MovieCatalogSyncScheduler(
    private val tmdbClient: TmdbClient,
    private val movieImporter: MovieImporter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 5 * * *")
    fun syncUpcomingMovies(): List<MediaItemResponse> {
        log.info("Starting scheduled upcoming-movies sync")
        val ids = tmdbClient.fetchUpcomingMovieIds()
        val results = mutableListOf<MediaItemResponse>()

        for (id in ids) {
            try {
                results.add(movieImporter.importMovie(id))
            } catch (ex: Exception) {
                log.error("Failed to import upcoming movie tmdbId={}, skipping it this run", id, ex)
            }
        }

        log.info("Movie sync: {} discovered from TMDb", ids.size)
        return results
    }
}
