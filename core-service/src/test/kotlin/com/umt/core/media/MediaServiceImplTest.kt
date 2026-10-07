package com.umt.core.media

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MovieResponse
import com.umt.api.generated.model.MusicResponse
import com.umt.api.generated.model.MediaSortOption
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.media.book.BookRepository
import com.umt.core.media.game.Game
import com.umt.core.media.game.GameRepository
import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieRepository
import com.umt.core.media.music.Music
import com.umt.core.media.music.MusicRepository
import com.umt.core.media.tvshow.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Root
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.domain.Specification
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class MediaServiceImplTest {

    private lateinit var movieRepository: MovieRepository
    private lateinit var tvShowRepository: TvShowRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var musicRepository: MusicRepository
    private lateinit var mediaResponseAssembler: MediaResponseAssembler
    private lateinit var mediaMapper: MediaMapper
    private lateinit var service: MediaServiceImpl

    private val fixedResponse = MovieResponse(
        id = UUID.randomUUID(),
        mediaCategory = ApiMediaCategory.MOVIE,
        title = "assembled",
        releaseDateStatus = ApiReleaseStatus.ANNOUNCED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.TMDB,
        externalSourceId = "1",
    )

    @BeforeEach
    fun setUp() {
        movieRepository = mockk()
        tvShowRepository = mockk()
        gameRepository = mockk()
        bookRepository = mockk()
        musicRepository = mockk()
        mediaResponseAssembler = mockk()
        mediaMapper = mockk()
        service = MediaServiceImpl(
            movieRepository, tvShowRepository, gameRepository, bookRepository, musicRepository,
            mediaResponseAssembler, mediaMapper,
        )
    }

    private fun movie(id: UUID = UUID.randomUUID()) = Movie(id = id, title = "t", tmdbId = id.toString())

    private fun response(id: UUID, releaseDate: LocalDate? = null, popularityScore: BigDecimal = BigDecimal.ZERO, delayProbability: BigDecimal? = null) =
        fixedResponse.copy(id = id, releaseDate = releaseDate, popularityScore = popularityScore, latestDelayProbability = delayProbability)

    private fun musicResponse(id: UUID) = MusicResponse(
        id = id,
        mediaCategory = ApiMediaCategory.MUSIC,
        title = "assembled",
        releaseDateStatus = ApiReleaseStatus.ANNOUNCED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.MUSICBRAINZ,
        externalSourceId = "1",
    )

    @Nested
    @DisplayName("listMedia")
    inner class ListMedia {

        @BeforeEach
        fun setUp() {
            every { mediaMapper.toDomainMediaCategory(ApiMediaCategory.MOVIE) } returns MediaCategory.MOVIE
            every { mediaMapper.toDomainReleaseStatus(ApiReleaseStatus.CONFIRMED) } returns ReleaseStatus.CONFIRMED
        }

        @Test
        fun `with no filters it runs one specification query on the movie repository`() {
            every { movieRepository.findAll(any<Specification<Movie>>()) } returns emptyList()
            every { mediaResponseAssembler.assembleMovieList(emptyList()) } returns emptyList()

            service.listMedia(ApiMediaCategory.MOVIE, status = null, sort = null)

            verify(exactly = 1) { movieRepository.findAll(any<Specification<Movie>>()) }
            verify(exactly = 0) { movieRepository.findAll() }
        }

        @Test
        fun `status and releaseDateFrom both reach the repository as one specification`() {
            val spec = slot<Specification<Movie>>()
            every { movieRepository.findAll(capture(spec)) } returns emptyList()
            every { mediaResponseAssembler.assembleMovieList(emptyList()) } returns emptyList()
            val from = LocalDate.of(2026, 9, 1)

            service.listMedia(ApiMediaCategory.MOVIE, ApiReleaseStatus.CONFIRMED, sort = null, releaseDateFrom = from)

            // Run against a stand-in criteria builder: what matters here is that both filters made
            // it into the predicate at all. MediaSpecificationsTest runs the real thing on Postgres.
            val cb = mockk<CriteriaBuilder>(relaxed = true)
            spec.captured.toPredicate(mockk<Root<Movie>>(relaxed = true), mockk<CriteriaQuery<*>>(relaxed = true), cb)
            verify { cb.equal(any<Expression<*>>(), ReleaseStatus.CONFIRMED) }
            verify { cb.greaterThanOrEqualTo(any<Expression<LocalDate>>(), from) }
        }

        // The when-over-MediaCategory dispatch is five near-identical branches - worth one check
        // that a non-default type actually reaches its own repository/assembler pair rather
        // than falling through to (or copy-pasting) the movie branch.
        @Test
        fun `a different media type dispatches to its own repository, not a copy-pasted sibling`() {
            every { mediaMapper.toDomainMediaCategory(ApiMediaCategory.GAME) } returns MediaCategory.GAME
            every { gameRepository.findAll(any<Specification<Game>>()) } returns emptyList()
            every { mediaResponseAssembler.assembleGameList(emptyList()) } returns emptyList()

            service.listMedia(ApiMediaCategory.GAME, status = null, sort = null)

            verify(exactly = 1) { gameRepository.findAll(any<Specification<Game>>()) }
            verify(exactly = 0) { movieRepository.findAll(any<Specification<Movie>>()) }
        }

        @Test
        fun `default sort orders by release date ascending, with unscheduled items last`() {
            val soon = UUID.randomUUID()
            val later = UUID.randomUUID()
            val tba = UUID.randomUUID()
            every { movieRepository.findAll(any<Specification<Movie>>()) } returns listOf(movie(later), movie(tba), movie(soon))
            every { mediaResponseAssembler.assembleMovieList(any()) } returns listOf(
                response(later, releaseDate = LocalDate.of(2027, 1, 1)),
                response(tba, releaseDate = null),
                response(soon, releaseDate = LocalDate.of(2026, 10, 2)),
            )

            val result = service.listMedia(ApiMediaCategory.MOVIE, status = null, sort = null)

            assertEquals(listOf(soon, later, tba), result.map { it.id })
        }

        @Test
        fun `DELAY_RISK sort orders by delay probability descending, with unscored items last`() {
            val highRisk = UUID.randomUUID()
            val lowRisk = UUID.randomUUID()
            val unscored = UUID.randomUUID()
            every { movieRepository.findAll(any<Specification<Movie>>()) } returns listOf(movie(lowRisk), movie(unscored), movie(highRisk))
            every { mediaResponseAssembler.assembleMovieList(any()) } returns listOf(
                response(lowRisk, delayProbability = BigDecimal.valueOf(9)),
                response(unscored, delayProbability = null),
                response(highRisk, delayProbability = BigDecimal.valueOf(94)),
            )

            val result = service.listMedia(ApiMediaCategory.MOVIE, status = null, sort = MediaSortOption.DELAY_RISK)

            assertEquals(listOf(highRisk, lowRisk, unscored), result.map { it.id })
        }

        @Test
        fun `POPULARITY sort orders by popularity score descending`() {
            val popular = UUID.randomUUID()
            val niche = UUID.randomUUID()
            every { movieRepository.findAll(any<Specification<Movie>>()) } returns listOf(movie(niche), movie(popular))
            every { mediaResponseAssembler.assembleMovieList(any()) } returns listOf(
                response(niche, popularityScore = BigDecimal.valueOf(33)),
                response(popular, popularityScore = BigDecimal.valueOf(93)),
            )

            val result = service.listMedia(ApiMediaCategory.MOVIE, status = null, sort = MediaSortOption.POPULARITY)

            assertEquals(listOf(popular, niche), result.map { it.id })
        }
    }

    @Nested
    @DisplayName("getMediaById")
    inner class GetMediaById {

        @Test
        fun `returns the assembled response for an id found in the first table tried`() {
            val id = UUID.randomUUID()
            val existingMovie = movie(id)
            every { movieRepository.findById(id) } returns Optional.of(existingMovie)
            every { mediaResponseAssembler.assemble(existingMovie) } returns fixedResponse.copy(id = id)

            val result = service.getMediaById(id)

            assertEquals(id, result.id)
            verify(exactly = 0) { tvShowRepository.findById(any()) }
        }

        // id alone doesn't say which table to look in - this pins down that the fifth (last)
        // table is actually tried, not just the fast-path first hit.
        @Test
        fun `an id not found until the last table tried still resolves`() {
            val id = UUID.randomUUID()
            val music = Music(id = id, title = "In Rainbows", musicbrainzId = "xyz")
            every { movieRepository.findById(id) } returns Optional.empty()
            every { tvShowRepository.findById(id) } returns Optional.empty()
            every { gameRepository.findById(id) } returns Optional.empty()
            every { bookRepository.findById(id) } returns Optional.empty()
            every { musicRepository.findById(id) } returns Optional.of(music)
            every { mediaResponseAssembler.assemble(music) } returns musicResponse(id)

            val result = service.getMediaById(id)

            assertEquals(id, result.id)
        }

        @Test
        fun `an unknown id throws NoSuchElementException, which the global handler turns into a 404`() {
            val id = UUID.randomUUID()
            every { movieRepository.findById(id) } returns Optional.empty()
            every { tvShowRepository.findById(id) } returns Optional.empty()
            every { gameRepository.findById(id) } returns Optional.empty()
            every { bookRepository.findById(id) } returns Optional.empty()
            every { musicRepository.findById(id) } returns Optional.empty()

            assertThrows(NoSuchElementException::class.java) { service.getMediaById(id) }
        }

        @Test
        fun `a known mediaCategory queries only that one table, not the other four`() {
            val id = UUID.randomUUID()
            val existingMovie = movie(id)
            every { mediaMapper.toDomainMediaCategory(ApiMediaCategory.MOVIE) } returns MediaCategory.MOVIE
            every { movieRepository.findById(id) } returns Optional.of(existingMovie)
            every { mediaResponseAssembler.assemble(existingMovie) } returns fixedResponse.copy(id = id)

            val result = service.getMediaById(id, ApiMediaCategory.MOVIE)

            assertEquals(id, result.id)
            verify(exactly = 0) { tvShowRepository.findById(any()) }
            verify(exactly = 0) { gameRepository.findById(any()) }
            verify(exactly = 0) { bookRepository.findById(any()) }
            verify(exactly = 0) { musicRepository.findById(any()) }
        }

        @Test
        fun `an id that doesn't exist under the given mediaCategory 404s, not a fallback search`() {
            val id = UUID.randomUUID()
            every { mediaMapper.toDomainMediaCategory(ApiMediaCategory.BOOK) } returns MediaCategory.BOOK
            every { bookRepository.findById(id) } returns Optional.empty()

            assertThrows(NoSuchElementException::class.java) { service.getMediaById(id, ApiMediaCategory.BOOK) }
            verify(exactly = 0) { movieRepository.findById(any()) }
            verify(exactly = 0) { tvShowRepository.findById(any()) }
            verify(exactly = 0) { gameRepository.findById(any()) }
            verify(exactly = 0) { musicRepository.findById(any()) }
        }
    }

    @Nested
    @DisplayName("getUserRecommendations")
    inner class GetUserRecommendations {

        @BeforeEach
        fun setUp() {
            every { movieRepository.findAll() } returns emptyList()
            every { tvShowRepository.findAll() } returns emptyList()
            every { gameRepository.findAll() } returns emptyList()
            every { bookRepository.findAll() } returns emptyList()
            every { musicRepository.findAll() } returns emptyList()
            every { mediaResponseAssembler.assembleMovieList(any()) } returns emptyList()
            every { mediaResponseAssembler.assembleTvShowList(any()) } returns emptyList()
            every { mediaResponseAssembler.assembleGameList(any()) } returns emptyList()
            every { mediaResponseAssembler.assembleBookList(any()) } returns emptyList()
            every { mediaResponseAssembler.assembleMusicList(any()) } returns emptyList()
        }

        @Test
        fun `pools assembled items from every table`() {
            every { mediaResponseAssembler.assembleMovieList(any()) } returns listOf(fixedResponse)



        }
    }
}
