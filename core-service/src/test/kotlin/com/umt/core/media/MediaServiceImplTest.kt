package com.umt.core.media

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaType as ApiMediaType
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.contribution.ContributorType
import com.umt.core.contribution.RoleType
import com.umt.core.media.hardcover.HardcoverAuthor
import com.umt.core.media.hardcover.HardcoverBook
import com.umt.core.media.hardcover.HardcoverClient
import com.umt.core.media.hardcover.HardcoverContribution
import com.umt.core.media.igdb.IgdbClient
import com.umt.core.media.igdb.IgdbCompany
import com.umt.core.media.igdb.IgdbGame
import com.umt.core.media.igdb.IgdbInvolvedCompany
import com.umt.core.media.metacritic.MetacriticAlbumsClient
import com.umt.core.media.metacritic.UpcomingAlbumCandidate
import com.umt.core.media.musicbrainz.MusicBrainzArtistCredit
import com.umt.core.media.musicbrainz.MusicBrainzArtistRef
import com.umt.core.media.musicbrainz.MusicBrainzClient
import com.umt.core.media.musicbrainz.MusicBrainzReleaseGroup
import com.umt.core.media.tmdb.TmdbCatalogImporter
import com.umt.core.media.tmdb.TmdbClient
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
import java.time.LocalDate
import java.util.UUID

class MediaServiceImplTest {

    private lateinit var mediaItemRepository: MediaRepository
    private lateinit var tmdbClient: TmdbClient
    private lateinit var tmdbCatalogImporter: TmdbCatalogImporter
    private lateinit var metacriticAlbumsClient: MetacriticAlbumsClient
    private lateinit var musicBrainzClient: MusicBrainzClient
    private lateinit var igdbClient: IgdbClient
    private lateinit var hardcoverClient: HardcoverClient
    private lateinit var mediaEventPublisher: MediaEventPublisher
    private lateinit var releaseDateSyncService: ReleaseDateSyncService
    private lateinit var contributorCreditService: ContributorCreditService
    private lateinit var mediaResponseAssembler: MediaResponseAssembler
    private lateinit var service: MediaServiceImpl

    private val fixedResponse = MediaItemResponse(
        id = UUID.randomUUID(),
        mediaType = ApiMediaType.MUSIC,
        title = "assembled",
        releaseDateStatus = ApiReleaseStatus.ANNOUNCED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.MUSICBRAINZ,
        externalSourceId = "1",
    )

    @BeforeEach
    fun setUp() {
        mediaItemRepository = mockk()
        tmdbClient = mockk()
        tmdbCatalogImporter = mockk()
        metacriticAlbumsClient = mockk()
        musicBrainzClient = mockk()
        igdbClient = mockk()
        hardcoverClient = mockk()
        mediaEventPublisher = mockk()
        releaseDateSyncService = mockk()
        contributorCreditService = mockk()
        mediaResponseAssembler = mockk()
        service = MediaServiceImpl(
            mediaItemRepository, tmdbClient, tmdbCatalogImporter, metacriticAlbumsClient, musicBrainzClient,
            igdbClient, hardcoverClient, mediaEventPublisher, releaseDateSyncService, contributorCreditService,
            mediaResponseAssembler,
        )

        every { mediaItemRepository.save(any<MediaItem>()) } answers { firstArg<MediaItem>() }
        every { mediaEventPublisher.publishIfUpcoming(any()) } returns Unit
        every { contributorCreditService.credit(any(), any(), any(), any(), any(), any()) } returns Unit
        every { mediaResponseAssembler.assemble(any()) } returns fixedResponse
    }

    private fun existingItem(source: ExternalSourceType, externalId: String, type: MediaType = MediaType.MUSIC) =
        MediaItem(
            id = UUID.randomUUID(),
            mediaType = type,
            title = "Existing",
            externalSource = source,
            externalSourceId = externalId,
        )

    @Nested
    @DisplayName("syncUpcomingAlbums")
    inner class SyncUpcomingAlbums {

        private val candidate = UpcomingAlbumCandidate("Radiohead", "The Bends", LocalDate.of(2027, 1, 1))

        @Test
        fun `an already-known album (by title) is re-synced without ever calling MusicBrainz`() {
            val existing = existingItem(ExternalSourceType.MUSICBRAINZ, "mbid-1")
            every {
                mediaItemRepository.findByMediaTypeAndTitleIgnoreCase(MediaType.MUSIC, "The Bends")
            } returns listOf(existing)
            every {
                releaseDateSyncService.updateIfChanged(existing, candidate.releaseDate, "Metacritic")
            } returns existing
            every { metacriticAlbumsClient.fetchUpcomingAlbums() } returns listOf(candidate)

            val result = service.syncUpcomingAlbums()

            assertEquals(listOf(fixedResponse), result)
            verify(exactly = 0) { musicBrainzClient.findReleaseGroup(any(), any()) }
        }

        @Test
        fun `no confident MusicBrainz match skips the candidate entirely`() {
            every {
                mediaItemRepository.findByMediaTypeAndTitleIgnoreCase(MediaType.MUSIC, "The Bends")
            } returns emptyList()
            every { musicBrainzClient.findReleaseGroup("Radiohead", "The Bends") } returns null
            every { metacriticAlbumsClient.fetchUpcomingAlbums() } returns listOf(candidate)

            val result = service.syncUpcomingAlbums()

            assertTrue(result.isEmpty())
            verify(exactly = 0) { mediaItemRepository.save(any<MediaItem>()) }
        }

        @Test
        fun `a match already known by MBID is skipped as a belt-and-suspenders check`() {
            val match = MusicBrainzReleaseGroup(
                id = "mbid-1", title = "The Bends", score = 100, primaryType = "Album",
                firstReleaseDate = "2027-01-01", artistCredit = emptyList(),
            )
            every {
                mediaItemRepository.findByMediaTypeAndTitleIgnoreCase(MediaType.MUSIC, "The Bends")
            } returns emptyList()
            every { musicBrainzClient.findReleaseGroup("Radiohead", "The Bends") } returns match
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.MUSICBRAINZ, "mbid-1")
            } returns existingItem(ExternalSourceType.MUSICBRAINZ, "mbid-1")
            every { metacriticAlbumsClient.fetchUpcomingAlbums() } returns listOf(candidate)

            val result = service.syncUpcomingAlbums()

            assertTrue(result.isEmpty())
            verify(exactly = 0) { mediaItemRepository.save(any<MediaItem>()) }
        }

        @Test
        fun `a genuinely new album with an artist credit is saved, credited, and published`() {
            val artistRef = MusicBrainzArtistRef(id = "artist-1", name = "Radiohead")
            val match = MusicBrainzReleaseGroup(
                id = "mbid-1", title = "The Bends", score = 100, primaryType = "Album",
                firstReleaseDate = "2027-01-01",
                artistCredit = listOf(MusicBrainzArtistCredit(name = "Radiohead", artist = artistRef)),
            )
            every {
                mediaItemRepository.findByMediaTypeAndTitleIgnoreCase(MediaType.MUSIC, "The Bends")
            } returns emptyList()
            every { musicBrainzClient.findReleaseGroup("Radiohead", "The Bends") } returns match
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.MUSICBRAINZ, "mbid-1")
            } returns null
            every { metacriticAlbumsClient.fetchUpcomingAlbums() } returns listOf(candidate)

            val result = service.syncUpcomingAlbums()

            assertEquals(listOf(fixedResponse), result)
            verify(exactly = 1) {
                contributorCreditService.credit(
                    any(),
                    ExternalSourceType.MUSICBRAINZ,
                    "artist-1",
                    "Radiohead",
                    RoleType.ARTIST,
                    any()
                )
            }
            verify(exactly = 1) { mediaEventPublisher.publishIfUpcoming(any()) }
        }

        @Test
        fun `a new album with no linked artist id is still saved, just not credited`() {
            val match = MusicBrainzReleaseGroup(
                id = "mbid-1", title = "The Bends", score = 100, primaryType = "Album",
                firstReleaseDate = "2027-01-01", artistCredit = emptyList(),
            )
            every {
                mediaItemRepository.findByMediaTypeAndTitleIgnoreCase(MediaType.MUSIC, "The Bends")
            } returns emptyList()
            every { musicBrainzClient.findReleaseGroup("Radiohead", "The Bends") } returns match
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.MUSICBRAINZ, "mbid-1")
            } returns null
            every { metacriticAlbumsClient.fetchUpcomingAlbums() } returns listOf(candidate)

            val result = service.syncUpcomingAlbums()

            assertEquals(listOf(fixedResponse), result)
            verify(exactly = 0) { contributorCreditService.credit(any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `a candidate that blows up is skipped without failing the rest of the run`() {
            val goodCandidate = UpcomingAlbumCandidate("OK Computer Artist", "OK Computer", LocalDate.of(2027, 2, 1))
            every {
                mediaItemRepository.findByMediaTypeAndTitleIgnoreCase(MediaType.MUSIC, "The Bends")
            } throws RuntimeException("boom")
            every {
                mediaItemRepository.findByMediaTypeAndTitleIgnoreCase(MediaType.MUSIC, "OK Computer")
            } returns listOf(existingItem(ExternalSourceType.MUSICBRAINZ, "mbid-2"))
            every { releaseDateSyncService.updateIfChanged(any(), any(), "Metacritic") } returns existingItem(
                ExternalSourceType.MUSICBRAINZ,
                "mbid-2"
            )
            every { metacriticAlbumsClient.fetchUpcomingAlbums() } returns listOf(candidate, goodCandidate)

            val result = service.syncUpcomingAlbums()

            assertEquals(1, result.size)
        }
    }

    @Nested
    @DisplayName("syncUpcomingGames")
    inner class SyncUpcomingGames {

        private fun game(id: Long = 1L, companies: List<IgdbInvolvedCompany> = emptyList()) = IgdbGame(
            id = id,
            name = "Half-Life 3",
            firstReleaseDate = LocalDate.of(2027, 1, 1).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond(),
            summary = "At last",
            cover = null,
            involvedCompanies = companies,
        )

        @Test
        fun `an already-known game is re-synced, not re-credited`() {
            val existing = existingItem(ExternalSourceType.IGDB, "1", MediaType.GAME)
            every { igdbClient.fetchUpcomingGames() } returns listOf(game())
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.IGDB, "1")
            } returns existing
            every { releaseDateSyncService.updateIfChanged(existing, any(), "IGDB") } returns existing

            val result = service.syncUpcomingGames()

            assertEquals(listOf(fixedResponse), result)
            verify(exactly = 0) { contributorCreditService.credit(any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `a new game credits its developer and its publisher separately`() {
            val studio = IgdbCompany(id = 50, name = "Valve")
            val involved = IgdbInvolvedCompany(company = studio, developer = true, publisher = true)
            every { igdbClient.fetchUpcomingGames() } returns listOf(game(companies = listOf(involved)))
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.IGDB, "1")
            } returns null

            val result = service.syncUpcomingGames()

            assertEquals(listOf(fixedResponse), result)
            verify(exactly = 1) {
                contributorCreditService.credit(
                    any(),
                    ExternalSourceType.IGDB,
                    "50",
                    "Valve",
                    RoleType.DEVELOPER,
                    ContributorType.ORGANIZATION
                )
            }
            verify(exactly = 1) {
                contributorCreditService.credit(
                    any(),
                    ExternalSourceType.IGDB,
                    "50",
                    "Valve",
                    RoleType.PUBLISHER,
                    ContributorType.ORGANIZATION
                )
            }
        }

        @Test
        fun `an involved company with no company reference is skipped without crediting or crashing`() {
            val involved = IgdbInvolvedCompany(company = null, developer = true, publisher = true)
            every { igdbClient.fetchUpcomingGames() } returns listOf(game(companies = listOf(involved)))
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.IGDB, "1")
            } returns null

            val result = service.syncUpcomingGames()

            assertEquals(listOf(fixedResponse), result)
            verify(exactly = 0) { contributorCreditService.credit(any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `a game that blows up is skipped without failing the rest of the run`() {
            every { igdbClient.fetchUpcomingGames() } returns listOf(game(id = 1), game(id = 2))
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.IGDB, "1")
            } throws RuntimeException("boom")
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.IGDB, "2")
            } returns null

            val result = service.syncUpcomingGames()

            assertEquals(1, result.size)
        }
    }

    @Nested
    @DisplayName("syncUpcomingBooks")
    inner class SyncUpcomingBooks {

        private fun book(id: Long = 1L, contributions: List<HardcoverContribution> = emptyList()) = HardcoverBook(
            id = id, title = "Dune", description = null, slug = "dune",
            releaseDate = "2027-01-01", image = null, contributions = contributions,
        )

        @Test
        fun `an already-known book is re-synced, not re-credited`() {
            val existing = existingItem(ExternalSourceType.HARDCOVER, "1", MediaType.BOOK)
            every { hardcoverClient.fetchUpcomingBooks() } returns listOf(book())
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.HARDCOVER, "1")
            } returns existing
            every { releaseDateSyncService.updateIfChanged(existing, any(), "Hardcover") } returns existing

            val result = service.syncUpcomingBooks()

            assertEquals(listOf(fixedResponse), result)
            verify(exactly = 0) { contributorCreditService.credit(any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `a new book with a primary author gets it credited as AUTHOR`() {
            val author = HardcoverAuthor(id = 7, name = "Frank Herbert")
            every {
                hardcoverClient.fetchUpcomingBooks()
            } returns listOf(book(contributions = listOf(HardcoverContribution(author = author, contribution = null))))
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.HARDCOVER, "1")
            } returns null

            val result = service.syncUpcomingBooks()

            assertEquals(listOf(fixedResponse), result)
            verify(exactly = 1) {
                contributorCreditService.credit(
                    any(),
                    ExternalSourceType.HARDCOVER,
                    "7",
                    "Frank Herbert",
                    RoleType.AUTHOR,
                    any()
                )
            }
        }

        @Test
        fun `a new book with no linked author is still saved, just not credited`() {
            every { hardcoverClient.fetchUpcomingBooks() } returns listOf(book(contributions = emptyList()))
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.HARDCOVER, "1")
            } returns null

            val result = service.syncUpcomingBooks()

            assertEquals(listOf(fixedResponse), result)
            verify(exactly = 0) { contributorCreditService.credit(any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `a book that blows up is skipped without failing the rest of the run`() {
            every { hardcoverClient.fetchUpcomingBooks() } returns listOf(book(id = 1), book(id = 2))
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.HARDCOVER, "1")
            } throws RuntimeException("boom")
            every {
                mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.HARDCOVER, "2")
            } returns null

            val result = service.syncUpcomingBooks()

            assertEquals(1, result.size)
        }
    }
}