package com.umt.core.media

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.BookResponse
import com.umt.api.generated.model.GameResponse
import com.umt.api.generated.model.MovieResponse
import com.umt.api.generated.model.MusicResponse
import com.umt.api.generated.model.TvShowResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.contribution.Contributor
import com.umt.core.contribution.ContributorType
import com.umt.core.contribution.Credit
import com.umt.core.contribution.CreditRepository
import com.umt.core.contribution.CreditedItem
import com.umt.core.contribution.RoleType
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
import com.umt.core.media.music.similarity.ArtistSimilarityService
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
    private lateinit var creditRepository: CreditRepository
    private lateinit var tmdbClient: TmdbClient
    private lateinit var igdbClient: IgdbClient
    private lateinit var hardcoverClient: HardcoverClient
    private lateinit var artistSimilarityService: ArtistSimilarityService
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
        creditRepository = mockk()
        tmdbClient = mockk()
        igdbClient = mockk()
        hardcoverClient = mockk()
        artistSimilarityService = mockk()
        mediaResponseAssembler = mockk()
        mediaMapper = mockk()
        service = SimilarMediaService(
            movieRepository, tvShowRepository, gameRepository, bookRepository, musicRepository, creditRepository,
            tmdbClient, igdbClient, hardcoverClient, artistSimilarityService, mediaResponseAssembler, mediaMapper,
        )
        every { mediaResponseAssembler.assembleMovieList(any()) } answers { firstArg<List<Movie>>().map(::movieResponse) }
        every { mediaResponseAssembler.assembleTvShowList(any()) } answers { firstArg<List<TvShow>>().map(::tvShowResponse) }
        every { mediaResponseAssembler.assembleGameList(any()) } answers { firstArg<List<Game>>().map(::gameResponse) }
        every { mediaResponseAssembler.assembleBookList(any()) } answers { firstArg<List<Book>>().map(::bookResponse) }
        every { mediaResponseAssembler.assembleMusicList(any()) } answers { firstArg<List<Music>>().map(::musicResponse) }
    }

    // Stand in for the real assembler: one response per item, titled after it, in the same
    // order it was given - lets tests assert on order/count without caring about enrichment.
    private fun movieResponse(item: MediaItem) = MovieResponse(
        id = item.id!!, mediaCategory = ApiMediaCategory.MOVIE, title = item.title,
        releaseDateStatus = ApiReleaseStatus.RELEASED, popularityScore = BigDecimal.ZERO,
        ratingCount = 0, externalSource = ApiExternalSourceType.TMDB, externalSourceId = "x",
    )

    private fun tvShowResponse(item: MediaItem) = TvShowResponse(
        id = item.id!!, mediaCategory = ApiMediaCategory.TV_SHOW, title = item.title,
        releaseDateStatus = ApiReleaseStatus.RELEASED, popularityScore = BigDecimal.ZERO,
        ratingCount = 0, externalSource = ApiExternalSourceType.TMDB, externalSourceId = "x",
    )

    private fun gameResponse(item: MediaItem) = GameResponse(
        id = item.id!!, mediaCategory = ApiMediaCategory.GAME, title = item.title,
        releaseDateStatus = ApiReleaseStatus.RELEASED, popularityScore = BigDecimal.ZERO,
        ratingCount = 0, externalSource = ApiExternalSourceType.IGDB, externalSourceId = "x",
    )

    private fun bookResponse(item: MediaItem) = BookResponse(
        id = item.id!!, mediaCategory = ApiMediaCategory.BOOK, title = item.title,
        releaseDateStatus = ApiReleaseStatus.RELEASED, popularityScore = BigDecimal.ZERO,
        ratingCount = 0, externalSource = ApiExternalSourceType.HARDCOVER, externalSourceId = "x",
    )

    private fun musicResponse(item: MediaItem) = MusicResponse(
        id = item.id!!, mediaCategory = ApiMediaCategory.MUSIC, title = item.title,
        releaseDateStatus = ApiReleaseStatus.RELEASED, popularityScore = BigDecimal.ZERO,
        ratingCount = 0, externalSource = ApiExternalSourceType.MUSICBRAINZ, externalSourceId = "x",
    )

    @Nested
    inner class UnknownId {
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
    inner class MusicAlbums {
        private val artist = "artist-src"
        private val source = album("Source")

        private fun album(title: String) = Music(id = UUID.randomUUID(), title = title, musicbrainzId = "mb-$title")

        private fun credit(albumId: UUID, artistMbid: String, role: RoleType = RoleType.ARTIST, source: ExternalSourceType = ExternalSourceType.MUSICBRAINZ) =
            Credit(
                mediaItemId = albumId, mediaCategory = MediaCategory.MUSIC, role = role,
                contributor = Contributor(
                    id = UUID.randomUUID(), contributorType = ContributorType.PERSON, name = "Artist $artistMbid",
                    externalSource = source, externalSourceId = artistMbid,
                ),
            )

        private fun credited(artistMbid: String, albumId: UUID) = object : CreditedItem {
            override val externalId = artistMbid
            override val mediaItemId = albumId
        }

        private fun findCredited(vararg artistMbids: String) = creditRepository.findCreditedItems(
            MediaCategory.MUSIC, RoleType.ARTIST, ExternalSourceType.MUSICBRAINZ, artistMbids.toList(),
        )

        @BeforeEach
        fun setUp() {
            every { movieRepository.findById(source.id!!) } returns Optional.empty()
            every { tvShowRepository.findById(source.id!!) } returns Optional.empty()
            every { gameRepository.findById(source.id!!) } returns Optional.empty()
            every { bookRepository.findById(source.id!!) } returns Optional.empty()
            every { musicRepository.findById(source.id!!) } returns Optional.of(source)
            every { creditRepository.findByMediaItemId(source.id!!) } returns listOf(credit(source.id!!, artist))
        }

        @Test
        fun `resolves the similar artists to albums in our catalog, best-matching artist first`() {
            val byA = album("By A")
            val firstByB = album("First by B")
            val secondByB = album("Second by B")
            every { artistSimilarityService.similarArtistMbids(artist, any()) } returns listOf("b", "a")
            every { findCredited("b", "a") } returns listOf(credited("a", byA.id!!), credited("b", firstByB.id!!), credited("b", secondByB.id!!))
            // Repo returns them in the "wrong" order on purpose - order must come from the similar artists, not this.
            every { musicRepository.findByIdIn(listOf(firstByB.id!!, secondByB.id!!, byA.id!!)) } returns listOf(byA, secondByB, firstByB)

            val result = service.getSimilarMedia(source.id!!)

            assertEquals(listOf("First by B", "Second by B", "By A"), result.map { it.title })
        }

        @Test
        fun `works the same when the mediaCategory is given`() {
            val byA = album("By A")
            every { mediaMapper.toDomainMediaCategory(ApiMediaCategory.MUSIC) } returns MediaCategory.MUSIC
            every { artistSimilarityService.similarArtistMbids(artist, any()) } returns listOf("a")
            every { findCredited("a") } returns listOf(credited("a", byA.id!!))
            every { musicRepository.findByIdIn(listOf(byA.id!!)) } returns listOf(byA)

            val result = service.getSimilarMedia(source.id!!, ApiMediaCategory.MUSIC)

            assertEquals(listOf("By A"), result.map { it.title })
        }

        @Test
        fun `never returns the album itself or an album by its own artist`() {
            val byA = album("By A")
            every { artistSimilarityService.similarArtistMbids(artist, any()) } returns listOf(artist, "a")
            every { findCredited("a") } returns listOf(credited("a", source.id!!), credited("a", byA.id!!))
            every { musicRepository.findByIdIn(listOf(byA.id!!)) } returns listOf(byA)

            val result = service.getSimilarMedia(source.id!!)

            assertEquals(listOf("By A"), result.map { it.title })
        }

        @Test
        fun `caps results at SIMILAR_MEDIA_LIMIT even when every similar artist has an album`() {
            val albums = (1..15).map { album("Album $it") }
            val artists = albums.indices.map { "artist-$it" }
            every { artistSimilarityService.similarArtistMbids(artist, any()) } returns artists
            every { creditRepository.findCreditedItems(MediaCategory.MUSIC, RoleType.ARTIST, ExternalSourceType.MUSICBRAINZ, artists) } returns
                albums.mapIndexed { i, a -> credited(artists[i], a.id!!) }
            every { musicRepository.findByIdIn(albums.map { it.id!! }) } returns albums

            val result = service.getSimilarMedia(source.id!!)

            assertEquals(SimilarMediaService.SIMILAR_MEDIA_LIMIT, result.size)
        }

        @Test
        fun `similar artists with no album in our catalog is an empty list, not an error`() {
            every { artistSimilarityService.similarArtistMbids(artist, any()) } returns listOf("a")
            every { findCredited("a") } returns emptyList()
            every { musicRepository.findByIdIn(emptyList()) } returns emptyList()

            assertTrue(service.getSimilarMedia(source.id!!).isEmpty())
        }

        @Test
        fun `an artist with no similar artists is an empty list without a catalog query`() {
            every { artistSimilarityService.similarArtistMbids(artist, any()) } returns emptyList()

            assertTrue(service.getSimilarMedia(source.id!!).isEmpty())
            verify(exactly = 0) { creditRepository.findCreditedItems(any(), any(), any(), any()) }
        }

        @Test
        fun `an album with no MusicBrainz artist credit has nothing to look up`() {
            every { creditRepository.findByMediaItemId(source.id!!) } returns listOf(
                credit(source.id!!, "someone", role = RoleType.WRITER),
                credit(source.id!!, "someone-else", source = ExternalSourceType.TMDB),
            )

            assertTrue(service.getSimilarMedia(source.id!!).isEmpty())
            verify(exactly = 0) { artistSimilarityService.similarArtistMbids(any(), any()) }
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
