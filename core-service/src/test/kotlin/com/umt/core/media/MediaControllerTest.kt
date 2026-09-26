package com.umt.core.media

import com.umt.api.generated.model.ExternalSourceType
import com.umt.api.generated.model.MediaItemRequest
import com.umt.api.generated.model.MediaResponse
import com.umt.api.generated.model.BookResponse
import com.umt.api.generated.model.MediaSortOption
import com.umt.api.generated.model.MediaCategory
import com.umt.api.generated.model.ReleaseStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * A thin pass-through to MediaService, wrapped in ResponseEntity.ok — nothing here does its
 * own logic, so these just confirm the wiring: each endpoint calls the right service method
 * and forwards its result as-is.
 */
class MediaControllerTest {

    private lateinit var mediaService: MediaService
    private lateinit var similarMediaService: SimilarMediaService
    private lateinit var controller: MediaController

    @BeforeEach
    fun setUp() {
        mediaService = mockk()
        similarMediaService = mockk()
        controller = MediaController(mediaService, similarMediaService)
    }

    private fun response(title: String): MediaResponse = BookResponse(
        id = UUID.randomUUID(),
        mediaCategory = MediaCategory.BOOK,
        title = title,
        releaseDateStatus = ReleaseStatus.ANNOUNCED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ExternalSourceType.HARDCOVER,
        externalSourceId = "1",
    )

    @Test
    fun `listMedia delegates to the service with all four query params`() {
        val results = listOf(response("Meridian Line"))
        val from = LocalDate.of(2026, 9, 1)
        every {
            mediaService.listMedia(
                MediaCategory.MOVIE,
                ReleaseStatus.CONFIRMED,
                MediaSortOption.DELAY_RISK,
                from
            )
        } returns results

        val result =
            controller.listMedia(MediaCategory.MOVIE, ReleaseStatus.CONFIRMED, MediaSortOption.DELAY_RISK, from)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(results, result.body)
        verify(exactly = 1) {
            mediaService.listMedia(
                MediaCategory.MOVIE,
                ReleaseStatus.CONFIRMED,
                MediaSortOption.DELAY_RISK,
                from
            )
        }
    }

    @Test
    fun `listMedia works with status, sort and releaseDateFrom all omitted`() {
        every { mediaService.listMedia(MediaCategory.MOVIE, null, null, null) } returns emptyList()

        val result = controller.listMedia(MediaCategory.MOVIE, null, null, null)

        assertEquals(emptyList<MediaResponse>(), result.body)
    }

    @Test
    fun `getMediaById delegates to the service with the given id and no mediaCategory`() {
        val id = UUID.randomUUID()
        val expected = response("Meridian Line")
        every { mediaService.getMediaById(id, null) } returns expected

        val result = controller.getMediaById(id, null)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(expected, result.body)
        verify(exactly = 1) { mediaService.getMediaById(id, null) }
    }

    @Test
    fun `getMediaById forwards mediaCategory through to the service, unchanged`() {
        val id = UUID.randomUUID()
        val expected = response("Project Hail Mary")
        every { mediaService.getMediaById(id, MediaCategory.BOOK) } returns expected

        val result = controller.getMediaById(id, MediaCategory.BOOK)

        assertEquals(expected, result.body)
        verify(exactly = 1) { mediaService.getMediaById(id, MediaCategory.BOOK) }
    }

    @Test
    fun `getSimilarMedia delegates to SimilarMediaService with the given id and no mediaCategory`() {
        val id = UUID.randomUUID()
        val results = listOf(response("Blade Runner 2049"))
        every { similarMediaService.getSimilarMedia(id, null) } returns results

        val result = controller.getSimilarMedia(id, null)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(results, result.body)
        verify(exactly = 1) { similarMediaService.getSimilarMedia(id, null) }
    }

    @Test
    fun `getSimilarMedia forwards mediaCategory through to SimilarMediaService, unchanged`() {
        val id = UUID.randomUUID()
        val results = listOf(response("Portal 3"))
        every { similarMediaService.getSimilarMedia(id, MediaCategory.GAME) } returns results

        val result = controller.getSimilarMedia(id, MediaCategory.GAME)

        assertEquals(results, result.body)
        verify(exactly = 1) { similarMediaService.getSimilarMedia(id, MediaCategory.GAME) }
    }

    @Test
    fun `getRecommendations delegates to the service with the request's userId`() {
        val results = listOf(response("Meridian Line"))
        every { mediaService.getUserRecommendations(userId = 42L) } returns results

        val result = controller.getRecommendations(MediaItemRequest(userId = 42L))

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(results, result.body)
        verify(exactly = 1) { mediaService.getUserRecommendations(userId = 42L) }
    }
}
