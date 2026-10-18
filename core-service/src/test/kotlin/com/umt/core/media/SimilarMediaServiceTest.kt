package com.umt.core.media

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.media.book.Book
import com.umt.core.media.book.BookRepository
import com.umt.core.media.book.hardcover.HardcoverClient
import com.umt.core.media.game.Game
import com.umt.core.media.game.GameRepository
import com.umt.core.media.game.igdb.IgdbClient
import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieRepository
import com.umt.core.media.movie.tmdb.TmdbClient
import com.umt.core.media.music.Music
import com.umt.core.media.music.MusicRepository
import com.umt.core.media.tvshow.TvShow
import com.umt.core.media.tvshow.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

/**
 * The one thing worth pinning down carefully here: candidate ids come back from the source API
 * in *its* ranked order, but resolving them via findByXIdIn is a single batched query with no
 * guaranteed row order - the mapNotNull-over-candidateIds pattern has to be the thing that
 * restores ranking, not incidental DB order. Most tests below check exactly that.
 */
class SimilarMediaServiceTest {

    private lateinit var movieRepository: MovieRepository
    private lateinit var tvShowRepository: TvShowRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var musicRepository: MusicRepository
    private lateinit var tmdbClient: TmdbClient
    private lateinit var igdbClient: IgdbClient
    private lateinit var hardcoverClient: HardcoverClient
    private lateinit var mediaResponseAssembler: MediaResponseAssembler
    private lateinit var mediaMapper: MediaMapper
    private lateinit var service: SimilarMediaService

    @BeforeEach
    fun setUp() {
        movieRepository = mockk()
        tvShowRepository = mockk()
        gameRepository = mockk()
        bookRepository = mockk()
        musicRepository = mockk()
        tmdbClient = mockk()
        igdbClient = mockk()
        hardcoverClient = mockk()
        mediaResponseAssembler = mockk()
        mediaMapper = mockk()
        service = SimilarMediaService(
            movieRepository, tvShowRepository, gameRepository, bookRepository, musicRepository,
            tmdbClient, igdbClient, hardcoverClient, mediaResponseAssembler, mediaMapper,
        )
        every { mediaResponseAssembler.assembleMovieList(any()) } answers { fixedResponses(firstArg()) }
        every { mediaResponseAssembler.assembleTvShowList(any()) } answers { fixedResponses(firstArg()) }
        every { mediaResponseAssembler.assembleGameList(any()) } answers { fixedResponses(firstArg()) }
        every { mediaResponseAssembler.assembleBookList(any()) } answers { fixedResponses(firstArg()) }
    }

    // Stands in for the real assembler: one response per item, titled after it, in the same
    // order it was given - lets tests assert on order/count without caring about enrichment.
    private fun fixedResponses(items: List<MediaItem>): List<MediaItemResponse> = items.map {
        MediaItemResponse(
            id = it.id!!, mediaCategory = ApiMediaCategory.MOVIE, title = it.title,
            releaseDateStatus = ApiReleaseStatus.RELEASED, popularityScore = BigDecimal.ZERO,
            ratingCount = 0, externalSource = ApiExternalSourceType.TMDB, externalSourceId = "x",
        )
    }

    @Nested
    inner class UnknownAndMusic {
        @Test
        fun `an unknown id throws NoSuchElementException`() {
            val id = UUID.randomUUID()
            every { movieRepository.findById(id) } returns Optional.empty()
            every { tvShowRepository.findById(id) } returns Optional.empty()
            every { gameRepository.findById(id) } returns Optional.empty()
            every { bookRepository.findById(id) } returns Optional.empty()
            every { musicRepository.findById(id) } returns Optional.empty()

            assertThrows(NoSuchElementException::class.java) { service.getSimilarMedia(id) }
        }

        @Test
        fun `a music item has no upstream source, so it's always an empty list, not an error`() {
            val id = UUID.randomUUID()
            every { movieRepository.findById(id) } returns Optional.empty()
            every { tvShowRepository.findById(id) } returns Optional.empty()
            every { gameRepository.findById(id) } returns Optional.empty()
            every { bookRepository.findById(id) } returns Optional.empty()
            every { musicRepository.findById(id) } returns Optional.of(Music(id = id, title = "In Rainbows", musicbrainzId = "mb-1"))

            val result = service.getSimilarMedia(id)

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class KnownMediaCategory {
        @Test
        fun `a known mediaCategory queries only that one table, not the other four`() {
            val id = UUID.randomUUID()
            val book = Book(id = id, title = "Project Hail Mary", hardcoverId = "235")
            every { mediaMapper.toDomainMediaCategory(ApiMediaCategory.BOOK) } returns MediaCategory.BOOK
            every { bookRepository.findById(id) } returns Optional.of(book)
            every { hardcoverClient.fetchSimilarBookIds(235L) } returns emptyList()
            every { bookRepository.findByHardcoverIdIn(emptyList()) } returns emptyList()

            val result = service.getSimilarMedia(id, ApiMediaCategory.BOOK)

            assertTrue(result.isEmpty())
            verify(exactly = 0) { movieRepository.findById(any()) }
            verify(exactly = 0) { tvShowRepository.findById(any()) }
            verify(exactly = 0) { gameRepository.findById(any()) }
            verify(exactly = 0) { musicRepository.findById(any()) }
        }

        @Test
        fun `an id that doesn't exist under the given mediaCategory 404s, not a fallback search`() {
            val id = UUID.randomUUID()
            every { mediaMapper.toDomainMediaCategory(ApiMediaCategory.MOVIE) } returns MediaCategory.MOVIE
            every { movieRepository.findById(id) } returns Optional.empty()

            assertThrows(NoSuchElementException::class.java) { service.getSimilarMedia(id, ApiMediaCategory.MOVIE) }
            verify(exactly = 0) { tvShowRepository.findById(any()) }
            verify(exactly = 0) { gameRepository.findById(any()) }
            verify(exactly = 0) { bookRepository.findById(any()) }
            verify(exactly = 0) { musicRepository.findById(any()) }
        }

        @Test
        fun `mediaCategory=MUSIC still 404s for an id that isn't actually music`() {
            val id = UUID.randomUUID()
            every { mediaMapper.toDomainMediaCategory(ApiMediaCategory.MUSIC) } returns MediaCategory.MUSIC
            every { musicRepository.findById(id) } returns Optional.empty()

            assertThrows(NoSuchElementException::class.java) { service.getSimilarMedia(id, ApiMediaCategory.MUSIC) }
        }
    }

    @Nested
    inner class Movies {
        @Test
        fun `resolves TMDb recommendation ids against our catalog, preserving TMDb's own order`() {
            val id = UUID.randomUUID()
            val movie = Movie(id = id, title = "Dune: Part Three", tmdbId = "1")
            val second = Movie(id = UUID.randomUUID(), title = "Arrival", tmdbId = "20")
            val first = Movie(id = UUID.randomUUID(), title = "Blade Runner 2049", tmdbId = "10")
            every { movieRepository.findById(id) } returns Optional.of(movie)
            // TMDb ranks 10 first, then an id we don't have (15), then 20.
            every { tmdbClient.fetchMovieRecommendationIds(1L) } returns listOf(10L, 15L, 20L)
            // Repo returns them in the "wrong" order on purpose - order must come from candidateIds, not this.
            every { movieRepository.findByTmdbIdIn(listOf("10", "15", "20")) } returns listOf(second, first)

            val result = service.getSimilarMedia(id)

            assertEquals(listOf("Blade Runner 2049", "Arrival"), result.map { it.title })
        }

        @Test
        fun `caps results at SIMILAR_MEDIA_LIMIT even when every candidate matches`() {
            val id = UUID.randomUUID()
            val movie = Movie(id = id, title = "Dune: Part Three", tmdbId = "1")
            val candidateIds = (1..15L).map { it + 100 }
            val known = candidateIds.map { Movie(id = UUID.randomUUID(), title = "Movie $it", tmdbId = it.toString()) }
            every { movieRepository.findById(id) } returns Optional.of(movie)
            every { tmdbClient.fetchMovieRecommendationIds(1L) } returns candidateIds
            every { movieRepository.findByTmdbIdIn(candidateIds.map { it.toString() }) } returns known

            val result = service.getSimilarMedia(id)

            assertEquals(SimilarMediaService.SIMILAR_MEDIA_LIMIT, result.size)
        }

        @Test
        fun `no matches in our catalog is an empty list, not an error`() {
            val id = UUID.randomUUID()
            val movie = Movie(id = id, title = "Dune: Part Three", tmdbId = "1")
            every { movieRepository.findById(id) } returns Optional.of(movie)
            every { tmdbClient.fetchMovieRecommendationIds(1L) } returns listOf(999L)
            every { movieRepository.findByTmdbIdIn(listOf("999")) } returns emptyList()

            val result = service.getSimilarMedia(id)

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class OtherTypes {
        // Movie above already covers the ordering/capping/empty-match logic in full - these are
        // thin wiring checks that tv_show/game/book each reach their own client+repository pair.
        @Test
        fun `a tv show uses TMDb's recommendations, not the movie client call`() {
            val id = UUID.randomUUID()
            val show = TvShow(id = id, title = "Severance", tmdbId = "5")
            val match = TvShow(id = UUID.randomUUID(), title = "Silo", tmdbId = "6")
            every { tvShowRepository.findById(id) } returns Optional.of(show)
            every { movieRepository.findById(id) } returns Optional.empty()
            every { tmdbClient.fetchTvRecommendationIds(5L) } returns listOf(6L)
            every { tvShowRepository.findByTmdbIdIn(listOf("6")) } returns listOf(match)

            val result = service.getSimilarMedia(id)

            assertEquals(listOf("Silo"), result.map { it.title })
        }

        @Test
        fun `a game uses IGDB's similar_games, not TMDb`() {
            val id = UUID.randomUUID()
            val game = Game(id = id, title = "Half-Life 3", igdbId = "1942")
            val match = Game(id = UUID.randomUUID(), title = "Portal 3", igdbId = "478")
            every { movieRepository.findById(id) } returns Optional.empty()
            every { tvShowRepository.findById(id) } returns Optional.empty()
            every { gameRepository.findById(id) } returns Optional.of(game)
            every { igdbClient.fetchSimilarGameIds(1942L) } returns listOf(478L)
            every { gameRepository.findByIgdbIdIn(listOf("478")) } returns listOf(match)

            val result = service.getSimilarMedia(id)

            assertEquals(listOf("Portal 3"), result.map { it.title })
        }

        @Test
        fun `a book uses Hardcover's cached_similar_book_ids`() {
            val id = UUID.randomUUID()
            val book = Book(id = id, title = "Project Hail Mary", hardcoverId = "235")
            val match = Book(id = UUID.randomUUID(), title = "The Martian", hardcoverId = "58358")
            every { movieRepository.findById(id) } returns Optional.empty()
            every { tvShowRepository.findById(id) } returns Optional.empty()
            every { gameRepository.findById(id) } returns Optional.empty()
            every { bookRepository.findById(id) } returns Optional.of(book)
            every { hardcoverClient.fetchSimilarBookIds(235L) } returns listOf(58358L)
            every { bookRepository.findByHardcoverIdIn(listOf("58358")) } returns listOf(match)

            val result = service.getSimilarMedia(id)

            assertEquals(listOf("The Martian"), result.map { it.title })
        }
    }
}
