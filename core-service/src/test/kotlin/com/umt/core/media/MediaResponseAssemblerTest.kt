package com.umt.core.media

import com.umt.api.generated.model.BookResponse
import com.umt.api.generated.model.ContributorResponse
import com.umt.api.generated.model.ContributorType as ApiContributorType
import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.GameResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.MovieResponse
import com.umt.api.generated.model.MusicResponse
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.api.generated.model.TvShowResponse
import com.umt.core.media.book.Book
import com.umt.core.media.book.BookMapper
import com.umt.core.contribution.Contributor
import com.umt.core.contribution.ContributorMapper
import com.umt.core.contribution.ContributorType
import com.umt.core.contribution.Credit
import com.umt.core.contribution.CreditRepository
import com.umt.core.contribution.RoleType
import com.umt.core.media.game.Game
import com.umt.core.media.game.GameMapper
import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieMapper
import com.umt.core.media.movie.MovieMapperImpl
import com.umt.core.media.music.Music
import com.umt.core.media.music.MusicMapper
import com.umt.core.rumor.RumorSnapshot
import com.umt.core.rumor.RumorSnapshotRepository
import com.umt.core.rumor.TrendDirection
import com.umt.core.media.tvshow.TvShow
import com.umt.core.media.tvshow.TvShowMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class MediaResponseAssemblerTest {

    private lateinit var movieMapper: MovieMapper
    private lateinit var tvShowMapper: TvShowMapper
    private lateinit var gameMapper: GameMapper
    private lateinit var bookMapper: BookMapper
    private lateinit var musicMapper: MusicMapper
    private lateinit var contributorMapper: ContributorMapper
    private lateinit var creditRepository: CreditRepository
    private lateinit var rumorSnapshotRepository: RumorSnapshotRepository
    private lateinit var assembler: MediaResponseAssembler

    private val movieId = UUID.randomUUID()
    private val movie = Movie(id = movieId, title = "Inception", tmdbId = "27205")
    private val movieExtras = slot<ResponseExtras>()

    private fun movieResponse(id: UUID, title: String) = MovieResponse(
        id = id,
        mediaCategory = ApiMediaCategory.MOVIE,
        title = title,
        releaseDateStatus = ApiReleaseStatus.RELEASED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.TMDB,
        externalSourceId = "27205",
    )

    @BeforeEach
    fun setUp() {
        movieMapper = mockk()
        tvShowMapper = mockk()
        gameMapper = mockk()
        bookMapper = mockk()
        musicMapper = mockk()
        contributorMapper = mockk()
        creditRepository = mockk()
        rumorSnapshotRepository = mockk()
        assembler = MediaResponseAssembler(
            movieMapper, tvShowMapper, gameMapper, bookMapper, musicMapper,
            contributorMapper, creditRepository, rumorSnapshotRepository,
        )
        every { movieMapper.toResponse(movie, capture(movieExtras)) } returns movieResponse(movieId, "Inception")
        every { rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(movieId) } returns emptyList()
    }

    private fun snapshot(itemId: UUID, mediaCategory: MediaCategory, probability: Int, computedAt: Instant, trend: TrendDirection? = null) =
        RumorSnapshot(
            mediaItemId = itemId,
            mediaCategory = mediaCategory,
            delayProbability = BigDecimal.valueOf(probability.toLong()),
            confidenceTrend = trend,
            computedAt = computedAt,
        )

    private fun nolanCredit(itemId: UUID): Pair<Credit, ContributorResponse> {
        val contributor = Contributor(
            id = UUID.randomUUID(),
            contributorType = ContributorType.PERSON,
            name = "Christopher Nolan",
            externalSource = ExternalSourceType.TMDB,
            externalSourceId = "525",
        )
        val credit = Credit(mediaItemId = itemId, mediaCategory = MediaCategory.MOVIE, contributor = contributor, role = RoleType.DIRECTOR)
        val response = ContributorResponse(
            id = contributor.id!!,
            name = "Christopher Nolan",
            contributorType = ApiContributorType.PERSON,
            role = com.umt.api.generated.model.RoleType.DIRECTOR,
        )
        return credit to response
    }

    @Test
    fun `hands the mapper contributors from a fresh credit query`() {
        val (credit, contributorResponse) = nolanCredit(movieId)
        every { creditRepository.findByMediaItemId(movieId) } returns listOf(credit)
        every { contributorMapper.toContributorResponse(credit) } returns contributorResponse

        val result = assembler.assemble(movie)

        assertEquals(listOf(contributorResponse), movieExtras.captured.contributors)
        assertEquals("Inception", result.title)
    }

    @Test
    fun `an item with no credits gets an empty contributors list`() {
        every { creditRepository.findByMediaItemId(movieId) } returns emptyList()

        assembler.assemble(movie)

        assertTrue(movieExtras.captured.contributors.isEmpty())
    }

    @Test
    fun `hands the mapper the delay signal from the most recent rumor_snapshot`() {
        every { creditRepository.findByMediaItemId(movieId) } returns emptyList()
        every { rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(movieId) } returns listOf(
            snapshot(movieId, MediaCategory.MOVIE, probability = 9, computedAt = Instant.parse("2026-09-21T09:00:00Z"), trend = TrendDirection.FALLING),
        )

        assembler.assemble(movie)

        assertEquals(BigDecimal.valueOf(9), movieExtras.captured.delayProbability)
        assertEquals(com.umt.api.generated.model.TrendDirection.FALLING, movieExtras.captured.confidenceTrend)
    }

    @Test
    fun `an item with no rumor_snapshot yet gets a null signal, not zero`() {
        every { creditRepository.findByMediaItemId(movieId) } returns emptyList()

        assembler.assemble(movie)

        assertNull(movieExtras.captured.delayProbability)
        assertNull(movieExtras.captured.confidenceTrend)
    }

    @Test
    fun `assembleMovieList enriches every item from one batched query per table`() {
        val other = Movie(id = UUID.randomUUID(), title = "Dune Part Three", tmdbId = "999")
        val (credit, contributorResponse) = nolanCredit(movieId)
        // Two snapshots for the same item, out of order - the newer one should win.
        val older = snapshot(movieId, MediaCategory.MOVIE, probability = 20, computedAt = Instant.parse("2026-09-01T00:00:00Z"))
        val newer = snapshot(movieId, MediaCategory.MOVIE, probability = 9, computedAt = Instant.parse("2026-09-21T00:00:00Z"))
        val otherExtras = slot<ResponseExtras>()

        every { movieMapper.toResponse(other, capture(otherExtras)) } returns movieResponse(other.id!!, "Dune Part Three")
        every { creditRepository.findByMediaItemIdIn(listOf(movieId, other.id!!)) } returns listOf(credit)
        every { contributorMapper.toContributorResponse(credit) } returns contributorResponse
        every { rumorSnapshotRepository.findByMediaItemIdIn(listOf(movieId, other.id!!)) } returns listOf(older, newer)

        val results = assembler.assembleMovieList(listOf(movie, other))

        assertEquals(listOf("Inception", "Dune Part Three"), results.map { it.title })
        assertEquals(listOf(contributorResponse), movieExtras.captured.contributors)
        assertEquals(BigDecimal.valueOf(9), movieExtras.captured.delayProbability)
        assertTrue(otherExtras.captured.contributors.isEmpty())
        assertNull(otherExtras.captured.delayProbability)
    }

    @Test
    fun `assembleMovieList on an empty list makes no queries`() {
        val results = assembler.assembleMovieList(emptyList())

        assertTrue(results.isEmpty())
    }

    @Test
    fun `the real movie mapper puts the extras on the response`() {
        val (credit, contributorResponse) = nolanCredit(movieId)
        every { creditRepository.findByMediaItemId(movieId) } returns listOf(credit)
        every { contributorMapper.toContributorResponse(credit) } returns contributorResponse
        every { rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(movieId) } returns listOf(
            snapshot(movieId, MediaCategory.MOVIE, probability = 9, computedAt = Instant.parse("2026-09-21T09:00:00Z"), trend = TrendDirection.FALLING),
        )
        val realAssembler = MediaResponseAssembler(
            MovieMapperImpl(), tvShowMapper, gameMapper, bookMapper, musicMapper,
            contributorMapper, creditRepository, rumorSnapshotRepository,
        )

        val result = realAssembler.assemble(Movie(id = movieId, title = "Inception", tmdbId = "27205", runtimeMinutes = 148))

        assertEquals(listOf(contributorResponse), result.contributors)
        assertEquals(BigDecimal.valueOf(9), result.latestDelayProbability)
        assertEquals(com.umt.api.generated.model.TrendDirection.FALLING, result.latestConfidenceTrend)
        assertEquals(148, result.runtimeMinutes)
    }

    // The five assemble()/assembleXList() overloads are near-identical boilerplate around the
    // shared extrasOf()/assembleAll() helpers - these four are thin "wiring" checks (right mapper,
    // right repository query) rather than a full re-test of the contributor/rumor logic already
    // covered above for movies.
    @Test
    fun `assembleGameList routes to the game mapper, not a copy-pasted sibling`() {
        val game = Game(id = UUID.randomUUID(), title = "Half-Life 3", igdbId = "1")
        every { gameMapper.toResponse(game, any()) } returns GameResponse(
            id = game.id!!, mediaCategory = ApiMediaCategory.GAME, title = "Half-Life 3",
            releaseDateStatus = ApiReleaseStatus.RELEASED, popularityScore = BigDecimal.ZERO,
            ratingCount = 0, externalSource = ApiExternalSourceType.IGDB, externalSourceId = "1",
        )
        every { creditRepository.findByMediaItemIdIn(listOf(game.id!!)) } returns emptyList()
        every { rumorSnapshotRepository.findByMediaItemIdIn(listOf(game.id!!)) } returns emptyList()

        val results = assembler.assembleGameList(listOf(game))

        assertEquals("Half-Life 3", results.single().title)
    }

    @Test
    fun `assemble(TvShow) routes to the tv show mapper`() {
        val tvShow = TvShow(id = UUID.randomUUID(), title = "Severance", tmdbId = "12345")
        every { tvShowMapper.toResponse(tvShow, any()) } returns TvShowResponse(
            id = tvShow.id!!, mediaCategory = ApiMediaCategory.TV_SHOW, title = "Severance",
            releaseDateStatus = ApiReleaseStatus.RELEASED, popularityScore = BigDecimal.ZERO,
            ratingCount = 0, externalSource = ApiExternalSourceType.TMDB, externalSourceId = "12345",
        )
        every { creditRepository.findByMediaItemId(tvShow.id!!) } returns emptyList()
        every { rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(tvShow.id!!) } returns emptyList()

        val result = assembler.assemble(tvShow)

        assertEquals("Severance", result.title)
    }

    @Test
    fun `assemble(Book) routes to the book mapper`() {
        val book = Book(id = UUID.randomUUID(), title = "Project Hail Mary", hardcoverId = "abc")
        every { bookMapper.toResponse(book, any()) } returns BookResponse(
            id = book.id!!, mediaCategory = ApiMediaCategory.BOOK, title = "Project Hail Mary",
            releaseDateStatus = ApiReleaseStatus.RELEASED, popularityScore = BigDecimal.ZERO,
            ratingCount = 0, externalSource = ApiExternalSourceType.HARDCOVER, externalSourceId = "abc",
        )
        every { creditRepository.findByMediaItemId(book.id!!) } returns emptyList()
        every { rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(book.id!!) } returns emptyList()

        val result = assembler.assemble(book)

        assertEquals("Project Hail Mary", result.title)
    }

    @Test
    fun `assemble(Music) routes to the music mapper`() {
        val music = Music(id = UUID.randomUUID(), title = "In Rainbows", musicbrainzId = "xyz")
        every { musicMapper.toResponse(music, any()) } returns MusicResponse(
            id = music.id!!, mediaCategory = ApiMediaCategory.MUSIC, title = "In Rainbows",
            releaseDateStatus = ApiReleaseStatus.RELEASED, popularityScore = BigDecimal.ZERO,
            ratingCount = 0, externalSource = ApiExternalSourceType.MUSICBRAINZ, externalSourceId = "xyz",
        )
        every { creditRepository.findByMediaItemId(music.id!!) } returns emptyList()
        every { rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(music.id!!) } returns emptyList()

        val result = assembler.assemble(music)

        assertEquals("In Rainbows", result.title)
    }
}
