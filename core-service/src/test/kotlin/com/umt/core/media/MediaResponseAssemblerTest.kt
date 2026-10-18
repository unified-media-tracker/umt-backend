package com.umt.core.media

import com.umt.api.generated.model.ContributorResponse
import com.umt.api.generated.model.ContributorType as ApiContributorType
import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
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
import com.umt.core.media.music.Music
import com.umt.core.media.music.MusicMapper
import com.umt.core.rumor.RumorSnapshot
import com.umt.core.rumor.RumorSnapshotRepository
import com.umt.core.rumor.TrendDirection
import com.umt.core.media.tvshow.TvShow
import com.umt.core.media.tvshow.TvShowMapper
import io.mockk.every
import io.mockk.mockk
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

    private fun baseResponse(id: UUID, title: String, mediaCategory: ApiMediaCategory = ApiMediaCategory.MOVIE) = MediaItemResponse(
        id = id,
        mediaCategory = mediaCategory,
        title = title,
        releaseDateStatus = ApiReleaseStatus.RELEASED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.TMDB,
        externalSourceId = "27205",
        contributors = emptyList(),
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
        every { movieMapper.toResponse(movie) } returns baseResponse(movieId, "Inception")
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

    @Test
    fun `fills in contributors from a fresh credit query`() {
        val contributor = Contributor(
            id = UUID.randomUUID(),
            contributorType = ContributorType.PERSON,
            name = "Christopher Nolan",
            externalSource = ExternalSourceType.TMDB,
            externalSourceId = "525",
        )
        val credit = Credit(mediaItemId = movieId, mediaCategory = MediaCategory.MOVIE, contributor = contributor, role = RoleType.DIRECTOR)
        val contributorResponse = ContributorResponse(
            id = contributor.id!!,
            name = "Christopher Nolan",
            contributorType = ApiContributorType.PERSON,
            role = com.umt.api.generated.model.RoleType.DIRECTOR,
        )
        every { creditRepository.findByMediaItemId(movieId) } returns listOf(credit)
        every { contributorMapper.toContributorResponse(credit) } returns contributorResponse

        val result = assembler.assemble(movie)

        assertEquals(listOf(contributorResponse), result.contributors)
        assertEquals("Inception", result.title)
    }

    @Test
    fun `an item with no credits gets an empty contributors list`() {
        every { creditRepository.findByMediaItemId(movieId) } returns emptyList()

        val result = assembler.assemble(movie)

        assertTrue(result.contributors.isNullOrEmpty())
    }

    @Test
    fun `fills in the delay signal from the most recent rumor_snapshot`() {
        every { creditRepository.findByMediaItemId(movieId) } returns emptyList()
        every { rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(movieId) } returns listOf(
            snapshot(movieId, MediaCategory.MOVIE, probability = 9, computedAt = Instant.parse("2026-09-21T09:00:00Z"), trend = TrendDirection.FALLING),
        )

        val result = assembler.assemble(movie)

        assertEquals(BigDecimal.valueOf(9), result.latestDelayProbability)
        assertEquals(com.umt.api.generated.model.TrendDirection.FALLING, result.latestConfidenceTrend)
    }

    @Test
    fun `an item with no rumor_snapshot yet gets a null signal, not zero`() {
        every { creditRepository.findByMediaItemId(movieId) } returns emptyList()

        val result = assembler.assemble(movie)

        assertNull(result.latestDelayProbability)
        assertNull(result.latestConfidenceTrend)
    }

    @Test
    fun `assembleMovieList enriches every item from one batched query per table`() {
        val other = Movie(id = UUID.randomUUID(), title = "Dune Part Three", tmdbId = "999")
        val credit = Credit(
            mediaItemId = movieId, mediaCategory = MediaCategory.MOVIE,
            contributor = Contributor(
                id = UUID.randomUUID(), contributorType = ContributorType.PERSON, name = "Christopher Nolan",
                externalSource = ExternalSourceType.TMDB, externalSourceId = "525",
            ),
            role = RoleType.DIRECTOR,
        )
        val contributorResponse = ContributorResponse(
            id = credit.contributor.id!!, name = "Christopher Nolan",
            contributorType = ApiContributorType.PERSON, role = com.umt.api.generated.model.RoleType.DIRECTOR,
        )
        // Two snapshots for the same item, out of order - the newer one should win.
        val older = snapshot(movieId, MediaCategory.MOVIE, probability = 20, computedAt = Instant.parse("2026-09-01T00:00:00Z"))
        val newer = snapshot(movieId, MediaCategory.MOVIE, probability = 9, computedAt = Instant.parse("2026-09-21T00:00:00Z"))

        every { movieMapper.toResponse(other) } returns baseResponse(other.id!!, "Dune Part Three")
        every { creditRepository.findByMediaItemIdIn(listOf(movieId, other.id!!)) } returns listOf(credit)
        every { contributorMapper.toContributorResponse(credit) } returns contributorResponse
        every { rumorSnapshotRepository.findByMediaItemIdIn(listOf(movieId, other.id!!)) } returns listOf(older, newer)

        val results = assembler.assembleMovieList(listOf(movie, other))

        val first = results.first { it.id == movieId }
        assertEquals(listOf(contributorResponse), first.contributors)
        assertEquals(BigDecimal.valueOf(9), first.latestDelayProbability)

        val second = results.first { it.id == other.id }
        assertTrue(second.contributors.isNullOrEmpty())
        assertNull(second.latestDelayProbability)
    }

    @Test
    fun `assembleMovieList on an empty list makes no queries`() {
        val results = assembler.assembleMovieList(emptyList())

        assertTrue(results.isEmpty())
    }

    // The five assemble()/assembleXList() overloads are near-identical boilerplate around the
    // shared enrich()/enrichBatch() helpers - these four are thin "wiring" checks (right mapper,
    // right repository query) rather than a full re-test of the contributor/rumor logic already
    // covered above for movies.
    @Test
    fun `assembleGameList routes to the game mapper, not a copy-pasted sibling`() {
        val game = Game(id = UUID.randomUUID(), title = "Half-Life 3", igdbId = "1")
        every { gameMapper.toResponse(game) } returns baseResponse(game.id!!, "Half-Life 3", ApiMediaCategory.GAME)
        every { creditRepository.findByMediaItemIdIn(listOf(game.id!!)) } returns emptyList()
        every { rumorSnapshotRepository.findByMediaItemIdIn(listOf(game.id!!)) } returns emptyList()

        val results = assembler.assembleGameList(listOf(game))

        assertEquals("Half-Life 3", results.single().title)
    }

    @Test
    fun `assemble(TvShow) routes to the tv show mapper`() {
        val tvShow = TvShow(id = UUID.randomUUID(), title = "Severance", tmdbId = "12345")
        every { tvShowMapper.toResponse(tvShow) } returns baseResponse(tvShow.id!!, "Severance", ApiMediaCategory.TV_SHOW)
        every { creditRepository.findByMediaItemId(tvShow.id!!) } returns emptyList()
        every { rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(tvShow.id!!) } returns emptyList()

        val result = assembler.assemble(tvShow)

        assertEquals("Severance", result.title)
    }

    @Test
    fun `assemble(Book) routes to the book mapper`() {
        val book = Book(id = UUID.randomUUID(), title = "Project Hail Mary", hardcoverId = "abc")
        every { bookMapper.toResponse(book) } returns baseResponse(book.id!!, "Project Hail Mary", ApiMediaCategory.BOOK)
        every { creditRepository.findByMediaItemId(book.id!!) } returns emptyList()
        every { rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(book.id!!) } returns emptyList()

        val result = assembler.assemble(book)

        assertEquals("Project Hail Mary", result.title)
    }

    @Test
    fun `assemble(Music) routes to the music mapper`() {
        val music = Music(id = UUID.randomUUID(), title = "In Rainbows", musicbrainzId = "xyz")
        every { musicMapper.toResponse(music) } returns baseResponse(music.id!!, "In Rainbows", ApiMediaCategory.MUSIC)
        every { creditRepository.findByMediaItemId(music.id!!) } returns emptyList()
        every { rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(music.id!!) } returns emptyList()

        val result = assembler.assemble(music)

        assertEquals("In Rainbows", result.title)
    }
}
