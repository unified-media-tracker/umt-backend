package com.umt.core.media.tvshow

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.contribution.RoleType
import com.umt.core.media.ContributorCreditService
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.Genre
import com.umt.core.media.GenreRepository
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaResponseAssembler
import com.umt.core.media.ReleaseDateSyncService
import com.umt.core.media.movie.tmdb.TmdbClient
import com.umt.core.media.tvshow.tmdb.TmdbCreator
import com.umt.core.media.tvshow.tmdb.TmdbTvShowResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class TvShowImporterTest {

    private lateinit var tvShowRepository: TvShowRepository
    private lateinit var genreRepository: GenreRepository
    private lateinit var tmdbClient: TmdbClient
    private lateinit var mediaResponseAssembler: MediaResponseAssembler
    private lateinit var mediaEventPublisher: MediaEventPublisher
    private lateinit var releaseDateSyncService: ReleaseDateSyncService
    private lateinit var contributorCreditService: ContributorCreditService
    private lateinit var importer: TvShowImporter

    private val fixedResponse = MediaItemResponse(
        id = UUID.randomUUID(),
        mediaCategory = ApiMediaCategory.TV_SHOW,
        title = "assembled",
        releaseDateStatus = ApiReleaseStatus.RELEASED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.TMDB,
        externalSourceId = "1399",
    )

    @BeforeEach
    fun setUp() {
        tvShowRepository = mockk()
        genreRepository = mockk()
        tmdbClient = mockk()
        mediaResponseAssembler = mockk()
        mediaEventPublisher = mockk()
        releaseDateSyncService = mockk()
        contributorCreditService = mockk()
        importer = TvShowImporter(
            tvShowRepository, genreRepository, tmdbClient,
            mediaResponseAssembler, mediaEventPublisher, releaseDateSyncService, contributorCreditService,
        )

        every { genreRepository.findByName(any()) } returns null
        every { genreRepository.save(any<Genre>()) } answers { firstArg<Genre>().apply { id = UUID.randomUUID() } }
        every { tvShowRepository.save(any<TvShow>()) } answers { firstArg<TvShow>() }
        every { mediaEventPublisher.requestAnalysisIfUpcoming(any()) } returns Unit
        every { contributorCreditService.credit(any<TvShow>(), any(), any(), any(), any(), any()) } returns Unit
        every { mediaResponseAssembler.assemble(any<TvShow>()) } returns fixedResponse
    }

    private fun existingTvShow(tmdbId: String) = TvShow(id = UUID.randomUUID(), title = "Old title", tmdbId = tmdbId)

    @Test
    fun `a brand-new show credits every creator as DIRECTOR`() {
        every { tvShowRepository.findByTmdbId("1399") } returns null
        every { tmdbClient.fetchTvShow(1399L) } returns TmdbTvShowResponse(
            id = 1399L,
            name = "Game of Thrones",
            status = "Ended",
            overview = "Nine noble families",
            posterPath = "/got.jpg",
            firstAirDate = "2011-04-17",
            genres = emptyList(),
            createdBy = listOf(
                TmdbCreator(9813, "David Benioff"),
                TmdbCreator(228068, "D. B. Weiss"),
            ),
        )

        val result = importer.importTvShow(1399L)

        assertEquals(fixedResponse, result)
        verify(exactly = 1) {
            contributorCreditService.credit(any<TvShow>(), ExternalSourceType.TMDB, "9813", "David Benioff", RoleType.DIRECTOR, any())
        }
        verify(exactly = 1) {
            contributorCreditService.credit(any<TvShow>(), ExternalSourceType.TMDB, "228068", "D. B. Weiss", RoleType.DIRECTOR, any())
        }
        verify(exactly = 1) { mediaEventPublisher.requestAnalysisIfUpcoming(any()) }
    }

    @Test
    fun `an already-known show only gets its release date re-checked, no re-crediting`() {
        val existing = existingTvShow("1399")
        every { tvShowRepository.findByTmdbId("1399") } returns existing
        every { tmdbClient.fetchTvShow(1399L) } returns TmdbTvShowResponse(
            id = 1399L, name = "Game of Thrones", status = "Ended", overview = null,
            posterPath = null, firstAirDate = "2011-04-17", genres = emptyList(), createdBy = emptyList(),
        )
        val updated = existingTvShow("1399")
        every {
            releaseDateSyncService.updateIfChanged(existing, LocalDate.of(2011, 4, 17), "TMDb")
        } returns updated
        every { mediaResponseAssembler.assemble(updated) } returns fixedResponse

        val result = importer.importTvShow(1399L)

        assertEquals(fixedResponse, result)
        verify(exactly = 0) { tvShowRepository.save(any()) }
        verify(exactly = 0) { contributorCreditService.credit(any<TvShow>(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { mediaEventPublisher.requestAnalysisIfUpcoming(any()) }
    }

    @Test
    fun `genres and createdBy default to empty lists when omitted`() {
        val show = TmdbTvShowResponse(
            id = 1399L, name = "Game of Thrones", status = "Ended",
            overview = null, posterPath = null, firstAirDate = null,
        )

        assertTrue(show.genres.isEmpty())
        assertTrue(show.createdBy.isEmpty())
    }
}
