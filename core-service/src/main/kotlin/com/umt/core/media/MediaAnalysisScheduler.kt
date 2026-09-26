package com.umt.core.media

import com.umt.core.media.MediaSpecifications.awaitingAnalysis
import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieRepository
import com.umt.core.rumor.RumorSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Re-requests analysis of the movies still to come. The analyser scores each request once, so
 * without this a movie is scored only when first imported, and its delay signal - and the
 * distance-to-release prior under it - freezes at that moment.
 */
@Component
class MediaAnalysisScheduler(
    private val movieRepository: MovieRepository,
    private val rumorSnapshotRepository: RumorSnapshotRepository,
    private val mediaEventPublisher: MediaEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // After the 04:00-05:30 catalog syncs and the 05:45 released sweep, so it sees today's dates
    // and statuses.
    @Scheduled(cron = "0 0 6 * * *")
    fun requestAnalysis() {
        requestAnalysis(DAILY_BUDGET)
    }

    // Never-scored movies first, then whoever was scored longest ago; the nearer release wins a tie.
    fun requestAnalysis(budget: Int) {
        val candidates = movieRepository.findAll(awaitingAnalysis())
        if (candidates.isEmpty()) return

        val lastScored = rumorSnapshotRepository.findLatestComputedAt(candidates.mapNotNull { it.id })
            .associate { it.mediaItemId to it.computedAt }
        val due = candidates.sortedWith(compareBy<Movie> { lastScored[it.id] }.thenBy { it.releaseDate }).take(budget)

        val requested = due.count { movie ->
            try {
                mediaEventPublisher.requestAnalysisIfUpcoming(movie)
                true
            } catch (ex: Exception) {
                log.error("Failed to request analysis of '{}', skipping it this run", movie.title, ex)
                false
            }
        }
        log.info("Requested analysis for {} of {} upcoming movies", requested, candidates.size)
    }

    companion object {
        const val DAILY_BUDGET = 60
    }
}
