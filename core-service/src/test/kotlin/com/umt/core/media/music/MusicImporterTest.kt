package com.umt.core.media.music

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MusicResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.contribution.RoleType
import com.umt.core.media.ContributorCreditService
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaResponseAssembler
import com.umt.core.media.ReleaseDateSyncService
import com.umt.core.media.music.metacritic.UpcomingMusicCandidate
import com.umt.core.media.music.musicbrainz.MusicBrainzArtistCredit
import com.umt.core.media.music.musicbrainz.MusicBrainzArtistRef
import com.umt.core.media.music.musicbrainz.MusicBrainzClient
import com.umt.core.media.music.musicbrainz.MusicBrainzReleaseGroup
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class MusicImporterTest {

    private lateinit var musicRepository: MusicRepository
    private lateinit var musicBrainzClient: MusicBrainzClient
    private lateinit var mediaResponseAssembler: MediaResponseAssembler
    private lateinit var mediaEventPublisher: MediaEventPublisher
    private lateinit var releaseDateSyncService: ReleaseDateSyncService
    private lateinit var contributorCreditService: ContributorCreditService
    private lateinit var importer: MusicImporter

    private val candidate = UpcomingMusicCandidate("Radiohead", "The Bends", LocalDate.of(2027, 1, 1))

    private val fixedResponse = MusicResponse(
        id = UUID.randomUUID(),
        mediaCategory = ApiMediaCategory.MUSIC,
        title = "assembled",
        releaseDateStatus = ApiReleaseStatus.ANNOUNCED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.MUSICBRAINZ,
        externalSourceId = "mbid-1",
    )

    @BeforeEach
    fun setUp() {
        musicRepository = mockk()
        musicBrainzClient = mockk()
        mediaResponseAssembler = mockk()
        mediaEventPublisher = mockk()
        releaseDateSyncService = mockk()
        contributorCreditService = mockk()
        importer = MusicImporter(
            musicRepository, musicBrainzClient, mediaResponseAssembler,
            mediaEventPublisher, releaseDateSyncService, contributorCreditService,
        )

        every { musicRepository.save(any<Music>()) } answers { firstArg<Music>() }
        every { mediaEventPublisher.requestAnalysisIfUpcoming(any()) } returns Unit
        every { contributorCreditService.credit(any<Music>(), any(), any(), any(), any(), any()) } returns Unit
        every { mediaResponseAssembler.assemble(any<Music>()) } returns fixedResponse
    }

    private fun existingMusic(musicbrainzId: String) = Music(id = UUID.randomUUID(), title = "The Bends", musicbrainzId = musicbrainzId)

    @Test
    fun `an already-known release (by title) is re-synced without ever calling MusicBrainz`() {
        val existing = existingMusic("mbid-1")
        every { musicRepository.findByTitleIgnoreCase("The Bends") } returns listOf(existing)
        every { releaseDateSyncService.updateIfChanged(existing, candidate.releaseDate, "Metacritic") } returns existing

        val result = importer.importCandidate(candidate)

        assertEquals(fixedResponse, result)
        verify(exactly = 0) { musicBrainzClient.findReleaseGroup(any(), any()) }
    }

    @Test
    fun `no confident MusicBrainz match skips the candidate entirely`() {
        every { musicRepository.findByTitleIgnoreCase("The Bends") } returns emptyList()
        every { musicBrainzClient.findReleaseGroup("Radiohead", "The Bends") } returns null

        val result = importer.importCandidate(candidate)

        assertNull(result)
        verify(exactly = 0) { musicRepository.save(any()) }
    }

    @Test
    fun `a match already known by MBID is skipped as a belt-and-suspenders check`() {
        val match = MusicBrainzReleaseGroup(
            id = "mbid-1", title = "The Bends", score = 100, primaryType = "Album",
            firstReleaseDate = "2027-01-01", artistCredit = emptyList(),
        )
        every { musicRepository.findByTitleIgnoreCase("The Bends") } returns emptyList()
        every { musicBrainzClient.findReleaseGroup("Radiohead", "The Bends") } returns match
        every { musicRepository.findByMusicbrainzId("mbid-1") } returns existingMusic("mbid-1")

        val result = importer.importCandidate(candidate)

        assertNull(result)
        verify(exactly = 0) { musicRepository.save(any()) }
    }

    @Test
    fun `a genuinely new release with an artist credit is saved, credited, and published`() {
        val artistRef = MusicBrainzArtistRef(id = "artist-1", name = "Radiohead")
        val match = MusicBrainzReleaseGroup(
            id = "mbid-1", title = "The Bends", score = 100, primaryType = "Album",
            firstReleaseDate = "2027-01-01",
            artistCredit = listOf(MusicBrainzArtistCredit(name = "Radiohead", artist = artistRef)),
        )
        every { musicRepository.findByTitleIgnoreCase("The Bends") } returns emptyList()
        every { musicBrainzClient.findReleaseGroup("Radiohead", "The Bends") } returns match
        every { musicRepository.findByMusicbrainzId("mbid-1") } returns null

        val result = importer.importCandidate(candidate)

        assertEquals(fixedResponse, result)
        verify(exactly = 1) {
            contributorCreditService.credit(any<Music>(), ExternalSourceType.MUSICBRAINZ, "artist-1", "Radiohead", RoleType.ARTIST, any())
        }
        verify(exactly = 1) { mediaEventPublisher.requestAnalysisIfUpcoming(any()) }
    }

    @Test
    fun `a new release with no linked artist id is still saved, just not credited`() {
        val match = MusicBrainzReleaseGroup(
            id = "mbid-1", title = "The Bends", score = 100, primaryType = "Album",
            firstReleaseDate = "2027-01-01", artistCredit = emptyList(),
        )
        every { musicRepository.findByTitleIgnoreCase("The Bends") } returns emptyList()
        every { musicBrainzClient.findReleaseGroup("Radiohead", "The Bends") } returns match
        every { musicRepository.findByMusicbrainzId("mbid-1") } returns null

        val result = importer.importCandidate(candidate)

        assertEquals(fixedResponse, result)
        verify(exactly = 0) { contributorCreditService.credit(any<Music>(), any(), any(), any(), any(), any()) }
    }
}
