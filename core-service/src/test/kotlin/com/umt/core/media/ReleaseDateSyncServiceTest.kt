package com.umt.core.media

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/**
 * The rule under test: a re-sync only writes when the source's date actually moved, and only
 * a later date counts as a delay. Everything here runs against mocked repositories — no
 * database, so it stays fast enough to run on every push.
 */
class ReleaseDateSyncServiceTest {

    private lateinit var mediaRepository: MediaRepository
    private lateinit var historyRepository: ReleaseStatusHistoryRepository
    private lateinit var service: ReleaseDateSyncService

    @BeforeEach
    fun setUp() {
        mediaRepository = mockk()
        historyRepository = mockk()
        service = ReleaseDateSyncService(mediaRepository, historyRepository)
        // save() echoes its argument back, the way Spring Data does for an already-managed
        // entity. The type argument is required: CrudRepository.save is generic in S, which
        // erases to Object, so a relaxed mock hands back an Object and the caller blows up on
        // the implicit cast.
        every { mediaRepository.save(any<MediaItem>()) } answers { firstArg<MediaItem>() }
        every { historyRepository.save(any<ReleaseStatusHistory>()) } answers {
            firstArg<ReleaseStatusHistory>()
        }
    }

    private fun mediaItem(
        releaseDate: LocalDate?,
        status: ReleaseStatus = ReleaseStatus.ANNOUNCED,
    ) = MediaItem(
        id = UUID.randomUUID(),
        mediaType = MediaType.GAME,
        title = "Half-Life 3",
        releaseDate = releaseDate,
        releaseDateStatus = status,
        externalSource = ExternalSourceType.IGDB,
        externalSourceId = "42",
    )

    @Nested
    @DisplayName("when nothing changed")
    inner class NoOp {

        @Test
        fun `a null incoming date leaves the item untouched and writes nothing`() {
            val existing = mediaItem(LocalDate.of(2026, 5, 1))

            val result = service.updateIfChanged(existing, incomingDate = null, sourceLabel = "IGDB")

            assertSame(existing, result)
            assertEquals(LocalDate.of(2026, 5, 1), result.releaseDate)
            verify(exactly = 0) { mediaRepository.save(any<MediaItem>()) }
            verify(exactly = 0) { historyRepository.save(any<ReleaseStatusHistory>()) }
        }

        @Test
        fun `an identical date writes nothing`() {
            val existing = mediaItem(LocalDate.of(2026, 5, 1))

            service.updateIfChanged(existing, LocalDate.of(2026, 5, 1), "IGDB")

            verify(exactly = 0) { mediaRepository.save(any<MediaItem>()) }
            verify(exactly = 0) { historyRepository.save(any<ReleaseStatusHistory>()) }
        }
    }

    @Nested
    @DisplayName("when the date moved")
    inner class DateMoved {

        @Test
        fun `a later date marks the item DELAYED and records history`() {
            val existing = mediaItem(LocalDate.of(2026, 5, 1))

            val result = service.updateIfChanged(existing, LocalDate.of(2026, 9, 1), "IGDB")

            assertEquals(LocalDate.of(2026, 9, 1), result.releaseDate)
            assertEquals(ReleaseStatus.DELAYED, result.releaseDateStatus)

            val history = slot<ReleaseStatusHistory>()
            verify(exactly = 1) { historyRepository.save(capture(history)) }
            assertEquals(ReleaseStatus.DELAYED, history.captured.status)
            assertEquals(
                "IGDB: release date moved from 2026-05-01 to 2026-09-01",
                history.captured.sourceNote,
            )
        }

        @Test
        fun `an earlier date updates the item but does not mark it DELAYED`() {
            val existing = mediaItem(LocalDate.of(2026, 9, 1))

            val result = service.updateIfChanged(existing, LocalDate.of(2026, 5, 1), "TMDb")

            assertEquals(LocalDate.of(2026, 5, 1), result.releaseDate)
            assertEquals(ReleaseStatus.ANNOUNCED, result.releaseDateStatus)
            verify(exactly = 1) { mediaRepository.save(any<MediaItem>()) }
        }

        @Test
        fun `a first-ever date is not a delay`() {
            val existing = mediaItem(releaseDate = null, status = ReleaseStatus.TBA)

            val result = service.updateIfChanged(existing, LocalDate.of(2027, 1, 1), "MusicBrainz")

            assertEquals(LocalDate.of(2027, 1, 1), result.releaseDate)
            assertEquals(ReleaseStatus.TBA, result.releaseDateStatus)

            val history = slot<ReleaseStatusHistory>()
            verify(exactly = 1) { historyRepository.save(capture(history)) }
            assertEquals(
                "MusicBrainz: release date moved from unset to 2027-01-01",
                history.captured.sourceNote,
            )
        }

        @Test
        fun `an already RELEASED item never becomes DELAYED`() {
            val existing = mediaItem(LocalDate.of(2026, 5, 1), status = ReleaseStatus.RELEASED)

            val result = service.updateIfChanged(existing, LocalDate.of(2026, 9, 1), "TMDb")

            assertEquals(LocalDate.of(2026, 9, 1), result.releaseDate)
            assertEquals(ReleaseStatus.RELEASED, result.releaseDateStatus)
        }
    }
}
