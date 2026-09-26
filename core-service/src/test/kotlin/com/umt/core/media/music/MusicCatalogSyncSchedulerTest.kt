package com.umt.core.media.music

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.media.music.metacritic.MetacriticMusicClient
import com.umt.core.media.music.metacritic.UpcomingMusicCandidate
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class MusicCatalogSyncSchedulerTest {

    private lateinit var metacriticMusicClient: MetacriticMusicClient
    private lateinit var musicImporter: MusicImporter
    private lateinit var scheduler: MusicCatalogSyncScheduler

    private val fixedResponse = MediaItemResponse(
        id = UUID.randomUUID(),
        mediaCategory = ApiMediaCategory.MUSIC,
        title = "assembled",
        releaseDateStatus = ApiReleaseStatus.ANNOUNCED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.MUSICBRAINZ,
        externalSourceId = "mbid-2",
    )

    @BeforeEach
    fun setUp() {
        metacriticMusicClient = mockk()
        musicImporter = mockk()
        scheduler = MusicCatalogSyncScheduler(metacriticMusicClient, musicImporter)
    }

    @Test
    fun `a candidate that blows up is skipped without failing the rest of the run`() {
        val badCandidate = UpcomingMusicCandidate("Radiohead", "The Bends", LocalDate.of(2027, 1, 1))
        val goodCandidate = UpcomingMusicCandidate("OK Computer Artist", "OK Computer", LocalDate.of(2027, 2, 1))
        every { metacriticMusicClient.fetchUpcomingMusic() } returns listOf(badCandidate, goodCandidate)
        every { musicImporter.importCandidate(badCandidate) } throws RuntimeException("boom")
        every { musicImporter.importCandidate(goodCandidate) } returns fixedResponse

        val result = scheduler.syncUpcomingMusic()

        assertEquals(1, result.size)
    }

    @Test
    fun `a candidate the importer skips (returns null) contributes nothing, without an error`() {
        val skipped = UpcomingMusicCandidate("Nobody", "No Match", LocalDate.of(2027, 1, 1))
        every { metacriticMusicClient.fetchUpcomingMusic() } returns listOf(skipped)
        every { musicImporter.importCandidate(skipped) } returns null

        val result = scheduler.syncUpcomingMusic()

        assertEquals(0, result.size)
    }
}
