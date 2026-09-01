package com.umt.core.media.tmdb

import com.umt.core.media.MediaService
import com.umt.core.media.movie.MovieService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TmdbCatalogSyncScheduler(
    private val mediaService: MediaService,
    private val movieService: MovieService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 5 * * *")
    fun syncUpcomingMovies() {
        log.info("Starting scheduled upcoming-movies sync")
        movieService.syncUpcomingMovies()
    }

    @Scheduled(cron = "0 15 5 * * *")
    fun syncUpcomingTvSeries() {
        log.info("Starting scheduled upcoming-tv-series sync")
        mediaService.syncUpcomingTvSeries()
    }
}
