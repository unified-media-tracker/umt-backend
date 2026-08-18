package com.umt.core.media.tmdb

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaType as ApiMediaType
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.contribution.RoleType
import com.umt.core.media.ContributorCreditService
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaItem
import com.umt.core.media.MediaRepository
import com.umt.core.media.MediaResponseAssembler
import com.umt.core.media.MediaType
import com.umt.core.media.MovieDetails
import com.umt.core.media.MovieDetailsRepository
import com.umt.core.media.ReleaseDateSyncService
import com.umt.core.media.genre.Genre
import com.umt.core.media.genre.GenreRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

/**
 * Both importMovie and importTvShow branch the same way: an already-known item only gets its
 * release date re-checked, a brand-new one gets saved and has its crew/creators credited. Only
 * the "new" branch should ever touch ContributorCreditService — re-syncing an existing item
 * must not create duplicate credits.
 */
class TmdbCatalogImporterTest {

    private lateinit var mediaItemRepository: MediaRepository
    private lateinit var movieDetailsRepository: MovieDetailsRepository
    private lateinit var genreRepository: GenreRepository
    private lateinit var tmdbClient: TmdbClient
    private lateinit var mediaResponseAssembler: MediaResponseAssembler
    private lateinit var mediaEventPublisher: MediaEventPublisher
    private lateinit var releaseDateSyncService: ReleaseDateSyncService
    private lateinit var contributorCreditService: ContributorCreditService
    private lateinit var importer: TmdbCatalogImporter

    private val fixedResponse = MediaItemResponse(
        id = UUID.randomUUID(),
        mediaType = ApiMediaType.MOVIE,
        title = "assembled",
        releaseDateStatus = ApiReleaseStatus.RELEASED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.TMDB,
        externalSourceId = "1",
    )

    @BeforeEach
    fun setUp() {
        mediaItemRepository = mockk()
        movieDetailsRepository = mockk()
        genreRepository = mockk()
        tmdbClient = mockk()
        mediaResponseAssembler = mockk()
        mediaEventPublisher = mockk()
        releaseDateSyncService = mockk()
        contributorCreditService = mockk()
        importer = TmdbCatalogImporter(
            mediaItemRepository, movieDetailsRepository, genreRepository, tmdbClient,
            mediaResponseAssembler, mediaEventPublisher, releaseDateSyncService, contributorCreditService,
        )

        every { mediaItemRepository.save(any<MediaItem>()) } answers { firstArg<MediaItem>() }
        every { movieDetailsRepository.save(any<MovieDetails>()) } answers { firstArg<MovieDetails>() }
        every { genreRepository.findByName(any()) } returns null
        every { genreRepository.save(any<Genre>()) } answers { firstArg<Genre>().apply { id = UUID.randomUUID() } }
        every { mediaEventPublisher.publishIfUpcoming(any()) } returns Unit
        every { contributorCreditService.credit(any(), any(), any(), any(), any(), any()) } returns Unit
        every { mediaResponseAssembler.assemble(any()) } returns fixedResponse
    }

    private fun existingMediaItem(externalId: String) = MediaItem(
        id = UUID.randomUUID(),
        mediaType = MediaType.MOVIE,
        title = "Old title",
        releaseDate = null,
        externalSource = ExternalSourceType.TMDB,
        externalSourceId = externalId,
    )

    @Nested
    @DisplayName("importMovie")
    inner class ImportMovie {

        @Test
        fun `a brand-new movie is saved, gets its details stored, and its director+writers credited`() {
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.TMDB, "27205")
            } returns null
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

            val result = importer.importMovie(27205L)

            assertEquals(fixedResponse, result)
            verify(exactly = 1) { movieDetailsRepository.save(any<MovieDetails>()) }
            verify(exactly = 1) {
                contributorCreditService.credit(
                    any(), ExternalSourceType.TMDB, "525", "Christopher Nolan", RoleType.DIRECTOR, any(),
                )
            }
            verify(exactly = 1) {
                contributorCreditService.credit(
                    any(), ExternalSourceType.TMDB, "526", "Jonathan Nolan", RoleType.WRITER, any(),
                )
            }
            verify(exactly = 0) {
                contributorCreditService.credit(any(), any(), "700", any(), any(), any())
            }
            verify(exactly = 1) { mediaEventPublisher.publishIfUpcoming(any()) }
        }

        @Test
        fun `an already-known movie only gets its release date re-checked, no re-crediting`() {
            val existing = existingMediaItem("27205")
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.TMDB, "27205")
            } returns existing
            every { tmdbClient.fetchMovie(27205L) } returns TmdbMovieResponse(
                id = 27205L, title = "Inception", status = "Released", overview = null,
                posterPath = null, releaseDate = "2010-07-15", runtime = 148, genres = emptyList(),
            )
            val updated = existingMediaItem("27205")
            every {
                releaseDateSyncService.updateIfChanged(existing, java.time.LocalDate.of(2010, 7, 15), "TMDb")
            } returns updated
            every { mediaResponseAssembler.assemble(updated) } returns fixedResponse

            val result = importer.importMovie(27205L)

            assertEquals(fixedResponse, result)
            verify(exactly = 0) { mediaItemRepository.save(any<MediaItem>()) }
            verify(exactly = 0) { movieDetailsRepository.save(any<MovieDetails>()) }
            verify(exactly = 0) { contributorCreditService.credit(any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { mediaEventPublisher.publishIfUpcoming(any()) }
        }
    }

    @Nested
    @DisplayName("importTvShow")
    inner class ImportTvShow {

        @Test
        fun `a brand-new show credits every creator as DIRECTOR`() {
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.TMDB, "1399")
            } returns null
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
                contributorCreditService.credit(
                    any(), ExternalSourceType.TMDB, "9813", "David Benioff", RoleType.DIRECTOR, any(),
                )
            }
            verify(exactly = 1) {
                contributorCreditService.credit(
                    any(), ExternalSourceType.TMDB, "228068", "D. B. Weiss", RoleType.DIRECTOR, any(),
                )
            }
        }

        @Test
        fun `an already-known show only gets its release date re-checked, no re-crediting`() {
            val existing = existingMediaItem("1399")
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.TMDB, "1399")
            } returns existing
            every { tmdbClient.fetchTvShow(1399L) } returns TmdbTvShowResponse(
                id = 1399L, name = "Game of Thrones", status = "Ended", overview = null,
                posterPath = null, firstAirDate = "2011-04-17", genres = emptyList(), createdBy = emptyList(),
            )
            val updated = existingMediaItem("1399")
            every {
                releaseDateSyncService.updateIfChanged(existing, java.time.LocalDate.of(2011, 4, 17), "TMDb")
            } returns updated
            every { mediaResponseAssembler.assemble(updated) } returns fixedResponse

            val result = importer.importTvShow(1399L)

            assertEquals(fixedResponse, result)
            verify(exactly = 0) { contributorCreditService.credit(any(), any(), any(), any(), any(), any()) }
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
}
