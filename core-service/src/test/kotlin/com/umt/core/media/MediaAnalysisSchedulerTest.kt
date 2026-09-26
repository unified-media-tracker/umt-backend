package com.umt.core.media

import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieRepository
import com.umt.core.rumor.LatestAnalysis
import com.umt.core.rumor.RumorSnapshotRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.domain.Specification
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class MediaAnalysisSchedulerTest {

    private lateinit var movieRepository: MovieRepository
    private lateinit var rumorSnapshotRepository: RumorSnapshotRepository
    private lateinit var mediaEventPublisher: MediaEventPublisher
    private lateinit var scheduler: MediaAnalysisScheduler

    /** Every movie handed to the publisher, in the order it was requested. */
    private val requested = mutableListOf<MediaItem>()

    private val now = Instant.parse("2026-09-25T06:00:00Z")

    @BeforeEach
    fun setUp() {
        requested.clear()
        movieRepository = mockk()
        rumorSnapshotRepository = mockk()
        mediaEventPublisher = mockk()
        every { movieRepository.findAll(any<Specification<Movie>>()) } returns emptyList()
        every { rumorSnapshotRepository.findLatestComputedAt(any()) } returns emptyList()
        every { mediaEventPublisher.requestAnalysisIfUpcoming(capture(requested)) } returns Unit
        scheduler = MediaAnalysisScheduler(movieRepository, rumorSnapshotRepository, mediaEventPublisher)
    }

    private fun movie(title: String, releaseDate: LocalDate = LocalDate.of(2026, 10, 9)) =
        Movie(id = UUID.randomUUID(), title = title, tmdbId = title, releaseDate = releaseDate)

    private fun scored(movie: Movie, daysAgo: Long) = object : LatestAnalysis {
        override val mediaItemId = movie.id!!
        override val computedAt = now.minus(daysAgo, ChronoUnit.DAYS)
    }

    private fun requestedTitles() = requested.map { it.title }

    @Test
    fun `never-scored movies go first, then the longest unscored, and the budget cuts the rest`() {
        val yesterday = movie("Scored yesterday")
        val lastWeek = movie("Scored a week ago")
        val neverSoon = movie("Never scored, releasing soon", LocalDate.of(2026, 10, 2))
        val neverLater = movie("Never scored, releasing later", LocalDate.of(2026, 11, 20))
        every { movieRepository.findAll(any<Specification<Movie>>()) } returns listOf(yesterday, lastWeek, neverLater, neverSoon)
        every { rumorSnapshotRepository.findLatestComputedAt(any()) } returns listOf(scored(yesterday, 1), scored(lastWeek, 7))

        scheduler.requestAnalysis(budget = 3)

        assertEquals(
            listOf("Never scored, releasing soon", "Never scored, releasing later", "Scored a week ago"),
            requestedTitles(),
        )
    }

    @Test
    fun `a budget larger than the backlog just requests everything`() {
        every { movieRepository.findAll(any<Specification<Movie>>()) } returns listOf(movie("A"), movie("B"))

        scheduler.requestAnalysis(budget = 10)

        assertEquals(2, requested.size)
    }

    @Test
    fun `the scheduled run stays within the daily budget`() {
        every { movieRepository.findAll(any<Specification<Movie>>()) } returns (1..70).map { movie("Movie $it") }

        scheduler.requestAnalysis()

        assertEquals(MediaAnalysisScheduler.DAILY_BUDGET, requested.size)
    }

    @Test
    fun `one movie failing to publish does not cost the others their refresh`() {
        val broken = movie("Broken")
        val fine = movie("Fine")
        every { movieRepository.findAll(any<Specification<Movie>>()) } returns listOf(broken, fine)
        every { mediaEventPublisher.requestAnalysisIfUpcoming(broken) } throws IllegalStateException("broker down")

        scheduler.requestAnalysis(budget = 10)

        assertEquals(listOf("Fine"), requestedTitles())
    }

    @Test
    fun `no upcoming movies means no requests and no snapshot lookup`() {
        scheduler.requestAnalysis(budget = 10)

        assertEquals(emptyList<MediaItem>(), requested)
        verify(exactly = 0) { rumorSnapshotRepository.findLatestComputedAt(any()) }
    }

    @Test
    fun `analysis is requested every morning after the syncs and the released sweep`() {
        val cron = CronExpression.parse(
            MediaAnalysisScheduler::class.java.getMethod("requestAnalysis").getAnnotation(Scheduled::class.java).cron,
        )

        // the released sweep runs at 05:45 and the last catalog sync at 05:30
        assertEquals(LocalDateTime.of(2026, 9, 25, 6, 0), cron.next(LocalDateTime.of(2026, 9, 25, 5, 45)))
        assertEquals(LocalDateTime.of(2026, 9, 26, 6, 0), cron.next(LocalDateTime.of(2026, 9, 25, 6, 0)))
    }
}
