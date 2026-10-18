package com.umt.core.media.movie

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
import com.umt.core.media.movie.tmdb.TmdbCredits
import com.umt.core.media.movie.tmdb.TmdbCrewMember
import com.umt.core.media.movie.tmdb.TmdbGenre
import com.umt.core.media.movie.tmdb.TmdbMovieResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class MovieImporterTest {

    private lateinit var movieRepository: MovieRepository
    private lateinit var genreRepository: GenreRepository
    private lateinit var tmdbClient: TmdbClient
    private lateinit var mediaResponseAssembler: MediaResponseAssembler
    private lateinit var mediaEventPublisher: MediaEventPublisher
    private lateinit var releaseDateSyncService: ReleaseDateSyncService
    private lateinit var contributorCreditService: ContributorCreditService
    private lateinit var importer: MovieImporter

    private val fixedResponse = MediaItemResponse(
        id = UUID.randomUUID(),
        mediaCategory = ApiMediaCategory.MOVIE,
        title = "assembled",
        releaseDateStatus = ApiReleaseStatus.RELEASED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.TMDB,
        externalSourceId = "27205",
    )

    @BeforeEach
    fun setUp() {
        movieRepository = mockk()
        genreRepository = mockk()
        tmdbClient = mockk()
        mediaResponseAssembler = mockk()
        mediaEventPublisher = mockk()
        releaseDateSyncService = mockk()
        contributorCreditService = mockk()
        importer = MovieImporter(
            movieRepository, genreRepository, tmdbClient,
            mediaResponseAssembler, mediaEventPublisher, releaseDateSyncService, contributorCreditService,
        )

        every { genreRepository.findByName(any()) } returns null
        every { genreRepository.save(any<Genre>()) } answers { firstArg<Genre>().apply { id = UUID.randomUUID() } }
        every { mediaEventPublisher.publishIfUpcoming(any()) } returns Unit
        every { contributorCreditService.credit(any<Movie>(), any(), any(), any(), any(), any()) } returns Unit
    }

    private fun existingMovie(tmdbId: String) = Movie(id = UUID.randomUUID(), title = "Old title", tmdbId = tmdbId)

    @Test
    fun `a brand-new movie is saved with its genres and runtime, and its director+writers credited`() {
        every { movieRepository.findByTmdbId("27205") } returns null
        every { tmdbClient.fetchMovie(27205L) } returns TmdbMovieResponse(
            id = 27205L,
            title = "Inception",
            status = "Released",
            overview = "A thief",
            posterPath = "/poster.jpg",
            releaseDate = "2010-07-15",
            runtime = 148,
            genres = listOf(TmdbGenre(28, "Action")),
            credits = TmdbCredits(
                crew = listOf(
                    TmdbCrewMember(525, "Christopher Nolan", "Director", "Directing"),
                    TmdbCrewMember(526, "Jonathan Nolan", "Writer", "Writing"),
                    // The same writer appears twice under different jobs in the same department -
                    // distinctBy(id) must collapse this into a single credit.
                    TmdbCrewMember(526, "Jonathan Nolan", "Screenplay", "Writing"),
                    // Not a director and not in the Writing department - must not be credited.
                    TmdbCrewMember(700, "Some Editor", "Editor", "Editing"),
                ),
            ),
        )
        val savedSlot = slot<Movie>()
        every { movieRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
        every { mediaResponseAssembler.assemble(any<Movie>()) } returns fixedResponse

        val result = importer.importMovie(27205L)

        assertEquals(fixedResponse, result)
        assertEquals(148, savedSlot.captured.runtimeMinutes)
        assertEquals(listOf("Action"), savedSlot.captured.genres.map { it.name })
        verify(exactly = 1) {
            contributorCreditService.credit(any<Movie>(), ExternalSourceType.TMDB, "525", "Christopher Nolan", RoleType.DIRECTOR, any())
        }
        verify(exactly = 1) {
            contributorCreditService.credit(any<Movie>(), ExternalSourceType.TMDB, "526", "Jonathan Nolan", RoleType.WRITER, any())
        }
        verify(exactly = 0) {
            contributorCreditService.credit(any<Movie>(), any(), "700", any(), any(), any())
        }
        verify(exactly = 1) { mediaEventPublisher.publishIfUpcoming(any()) }
    }

    @Test
    fun `an already-known movie only gets its release date re-checked, no re-crediting`() {
        val existing = existingMovie("27205")
        every { movieRepository.findByTmdbId("27205") } returns existing
        every { tmdbClient.fetchMovie(27205L) } returns TmdbMovieResponse(
            id = 27205L, title = "Inception", status = "Released", overview = null,
            posterPath = null, releaseDate = "2010-07-15", runtime = 148, genres = emptyList(),
        )
        val updated = existingMovie("27205")
        every {
            releaseDateSyncService.updateIfChanged(existing, LocalDate.of(2010, 7, 15), "TMDb")
        } returns updated
        every { mediaResponseAssembler.assemble(updated) } returns fixedResponse

        val result = importer.importMovie(27205L)

        assertEquals(fixedResponse, result)
        verify(exactly = 0) { movieRepository.save(any()) }
        verify(exactly = 0) { contributorCreditService.credit(any<Movie>(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { mediaEventPublisher.publishIfUpcoming(any()) }
    }
}
