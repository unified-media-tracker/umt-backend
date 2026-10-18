package com.umt.core.media

import com.umt.core.media.book.BookRepository
import com.umt.core.media.game.Game
import com.umt.core.media.game.GameRepository
import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieRepository
import com.umt.core.media.music.MusicRepository
import com.umt.core.media.tvshow.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/**
 * The rule under test: a re-sync only writes when the source's date actually moved, and only
 * a later date counts as a delay. Everything here runs against mocked repositories — no
 * database, so it stays fast enough to run on every push. Exercised mainly against Game (one
 * of the five updateIfChanged overloads); a couple of Movie-scoped tests below stand in for
 * the other four, which share applyIfChanged()'s logic but each wire their own repository.
 */
class ReleaseDateSyncServiceTest {

    private lateinit var movieRepository: MovieRepository
    private lateinit var tvShowRepository: TvShowRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var musicRepository: MusicRepository
    private lateinit var historyRepository: ReleaseStatusHistoryRepository
    private lateinit var mediaEventPublisher: MediaEventPublisher
    private lateinit var service: ReleaseDateSyncService

    @BeforeEach
    fun setUp() {
        movieRepository = mockk()
        tvShowRepository = mockk()
        gameRepository = mockk()
        bookRepository = mockk()
        musicRepository = mockk()
        historyRepository = mockk()
        mediaEventPublisher = mockk(relaxed = true)
        service = ReleaseDateSyncService(
            movieRepository, tvShowRepository, gameRepository, bookRepository, musicRepository,
            historyRepository, mediaEventPublisher,
        )
        // save() echoes its argument back, the way Spring Data does for an already-managed entity.
        every { gameRepository.save(any<Game>()) } answers { firstArg<Game>() }
        every { movieRepository.save(any<Movie>()) } answers { firstArg<Movie>() }
        every { historyRepository.save(any<ReleaseStatusHistory>()) } answers { firstArg<ReleaseStatusHistory>() }
    }

    private fun game(
        releaseDate: LocalDate?,
        status: ReleaseStatus = ReleaseStatus.ANNOUNCED,
        igdbId: String = "42",
    ) = Game(id = UUID.randomUUID(), title = "Half-Life 3", releaseDate = releaseDate, releaseDateStatus = status, igdbId = igdbId)

    @Nested
    @DisplayName("when nothing changed")
    inner class NoOp {

        @Test
        fun `a null incoming date leaves the item untouched and writes nothing`() {
            val existing = game(LocalDate.of(2026, 5, 1))

            val result = service.updateIfChanged(existing, incomingDate = null, sourceLabel = "IGDB")

            assertSame(existing, result)
            assertEquals(LocalDate.of(2026, 5, 1), result.releaseDate)
            verify(exactly = 0) { gameRepository.save(any()) }
            verify(exactly = 0) { historyRepository.save(any()) }
        }

        @Test
        fun `an identical date writes nothing`() {
            val existing = game(LocalDate.of(2026, 5, 1))

            service.updateIfChanged(existing, LocalDate.of(2026, 5, 1), "IGDB")

            verify(exactly = 0) { gameRepository.save(any()) }
            verify(exactly = 0) { historyRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("when the date moved")
    inner class DateMoved {

        @Test
        fun `a later date marks the item DELAYED and records history`() {
            val existing = game(LocalDate.of(2026, 5, 1))

            val result = service.updateIfChanged(existing, LocalDate.of(2026, 9, 1), "IGDB")

            assertEquals(LocalDate.of(2026, 9, 1), result.releaseDate)
            assertEquals(ReleaseStatus.DELAYED, result.releaseDateStatus)

            val history = slot<ReleaseStatusHistory>()
            verify(exactly = 1) { historyRepository.save(capture(history)) }
            assertEquals(ReleaseStatus.DELAYED, history.captured.status)
            assertEquals(
                "IGDB: release date moved from 2026-05-01 to 2026-09-01",
                history.captured.sourceNote,
            )
        }

        @Test
        fun `an earlier date updates the item but does not mark it DELAYED`() {
            val existing = game(LocalDate.of(2026, 9, 1))

            val result = service.updateIfChanged(existing, LocalDate.of(2026, 5, 1), "TMDb")

            assertEquals(LocalDate.of(2026, 5, 1), result.releaseDate)
            assertEquals(ReleaseStatus.ANNOUNCED, result.releaseDateStatus)
            verify(exactly = 1) { gameRepository.save(any()) }
        }

        @Test
        fun `a first-ever date is not a delay`() {
            val existing = game(releaseDate = null, status = ReleaseStatus.TBA)

            val result = service.updateIfChanged(existing, LocalDate.of(2027, 1, 1), "MusicBrainz")

            assertEquals(LocalDate.of(2027, 1, 1), result.releaseDate)
            assertEquals(ReleaseStatus.TBA, result.releaseDateStatus)

            val history = slot<ReleaseStatusHistory>()
            verify(exactly = 1) { historyRepository.save(capture(history)) }
            assertEquals(
                "MusicBrainz: release date moved from unset to 2027-01-01",
                history.captured.sourceNote,
            )
        }

        @Test
        fun `an already RELEASED item never becomes DELAYED`() {
            val existing = game(LocalDate.of(2026, 5, 1), status = ReleaseStatus.RELEASED)

            val result = service.updateIfChanged(existing, LocalDate.of(2026, 9, 1), "TMDb")

            assertEquals(LocalDate.of(2026, 9, 1), result.releaseDate)
            assertEquals(ReleaseStatus.RELEASED, result.releaseDateStatus)
        }

        // The five updateIfChanged overloads share applyIfChanged() but still each wire their
        // own repository's save() - a copy-paste mistake (e.g. movie's overload calling
        // gameRepository.save) would only show up here, not in the game-only tests above.
        @Test
        fun `a movie change saves through the movie repository, not a copy-pasted sibling`() {
            val existing = Movie(id = UUID.randomUUID(), title = "Dune Part Three", releaseDate = LocalDate.of(2026, 5, 1), tmdbId = "1")

            service.updateIfChanged(existing, LocalDate.of(2026, 9, 1), "TMDb")

            verify(exactly = 1) { movieRepository.save(existing) }
            verify(exactly = 0) { gameRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("checkForNewlyReleasedItems")
    inner class CheckForNewlyReleasedItems {

        private val today = LocalDate.of(2026, 8, 27)

        @BeforeEach
        fun setUp() {
            every { movieRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(any(), ReleaseStatus.RELEASED) } returns emptyList()
            every { tvShowRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(any(), ReleaseStatus.RELEASED) } returns emptyList()
            every { gameRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(any(), ReleaseStatus.RELEASED) } returns emptyList()
            every { bookRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(any(), ReleaseStatus.RELEASED) } returns emptyList()
            every { musicRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(any(), ReleaseStatus.RELEASED) } returns emptyList()
        }

        @Test
        fun `a past-due item is marked RELEASED, logged to history, and published`() {
            val item = game(LocalDate.of(2026, 8, 20), status = ReleaseStatus.ANNOUNCED)
            every {
                gameRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(today, ReleaseStatus.RELEASED)
            } returns listOf(item)

            service.checkForNewlyReleasedItems(today)

            assertEquals(ReleaseStatus.RELEASED, item.releaseDateStatus)
            verify(exactly = 1) { gameRepository.save(item) }

            val history = slot<ReleaseStatusHistory>()
            verify(exactly = 1) { historyRepository.save(capture(history)) }
            assertEquals(ReleaseStatus.RELEASED, history.captured.status)

            verify(exactly = 1) { mediaEventPublisher.publishReleased(item) }
        }

        @Test
        fun `an item releasing exactly today is included`() {
            val item = game(today, status = ReleaseStatus.CONFIRMED)
            every {
                gameRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(today, ReleaseStatus.RELEASED)
            } returns listOf(item)

            service.checkForNewlyReleasedItems(today)

            assertEquals(ReleaseStatus.RELEASED, item.releaseDateStatus)
            verify(exactly = 1) { mediaEventPublisher.publishReleased(item) }
        }

        @Test
        fun `nothing past-due means nothing is touched or published`() {
            service.checkForNewlyReleasedItems(today)

            verify(exactly = 0) { gameRepository.save(any()) }
            verify(exactly = 0) { historyRepository.save(any()) }
            verify(exactly = 0) { mediaEventPublisher.publishReleased(any()) }
        }

        @Test
        fun `defaults to today when no date is given`() {
            service.checkForNewlyReleasedItems()

            verify(exactly = 1) {
                gameRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(LocalDate.now(), ReleaseStatus.RELEASED)
            }
        }

        @Test
        fun `multiple past-due items are each processed independently`() {
            val first = game(LocalDate.of(2026, 8, 1), status = ReleaseStatus.ANNOUNCED, igdbId = "1")
            val second = game(LocalDate.of(2026, 8, 15), status = ReleaseStatus.CONFIRMED, igdbId = "2")
            every {
                gameRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(today, ReleaseStatus.RELEASED)
            } returns listOf(first, second)

            service.checkForNewlyReleasedItems(today)

            assertEquals(ReleaseStatus.RELEASED, first.releaseDateStatus)
            assertEquals(ReleaseStatus.RELEASED, second.releaseDateStatus)
            verify(exactly = 1) { mediaEventPublisher.publishReleased(first) }
            verify(exactly = 1) { mediaEventPublisher.publishReleased(second) }
        }

        // Sweeps all five tables independently - a bug scoped to one type (e.g. only movie
        // wired correctly) wouldn't show up in the game-only tests above.
        @Test
        fun `sweeps every table, not just one`() {
            val movie = Movie(
                id = UUID.randomUUID(), title = "Old Movie", releaseDate = LocalDate.of(2026, 8, 1),
                releaseDateStatus = ReleaseStatus.CONFIRMED, tmdbId = "1",
            )
            every {
                movieRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(today, ReleaseStatus.RELEASED)
            } returns listOf(movie)

            service.checkForNewlyReleasedItems(today)

            assertEquals(ReleaseStatus.RELEASED, movie.releaseDateStatus)
            verify(exactly = 1) { movieRepository.save(movie) }
        }
    }
}
