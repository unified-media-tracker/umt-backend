package com.umt.core.media

import com.umt.api.generated.model.ExternalSourceType
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaType
import com.umt.api.generated.model.ReleaseStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.UUID

/**
 * A thin pass-through to MediaService, wrapped in ResponseEntity.ok — nothing here does its
 * own logic, so these just confirm the wiring: each endpoint calls the right service method
 * and forwards its result as-is.
 */
class MediaControllerTest {

    private lateinit var mediaService: MediaService
    private lateinit var controller: MediaController

    @BeforeEach
    fun setUp() {
        mediaService = mockk()
        controller = MediaController(mediaService)
    }

    private fun response(title: String) = MediaItemResponse(
        id = UUID.randomUUID(),
        mediaType = MediaType.BOOK,
        title = title,
        releaseDateStatus = ReleaseStatus.ANNOUNCED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ExternalSourceType.HARDCOVER,
        externalSourceId = "1",
    )

    @Test
    fun `syncUpcomingBooks delegates to the service and returns its result as-is`() {
        val results = listOf(response("Dune"))
        every { mediaService.syncUpcomingBooks() } returns results

        val result = controller.syncUpcomingBooks()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(results, result.body)
        verify(exactly = 1) { mediaService.syncUpcomingBooks() }
    }

    @Test
    fun `importMovieFromTmdb delegates to the service with the given id`() {
        val expected = response("Inception")
        every { mediaService.importMovieFromTmdb(27205L) } returns expected

        val result = controller.importMovieFromTmdb(27205L)

        assertEquals(expected, result.body)
        verify(exactly = 1) { mediaService.importMovieFromTmdb(27205L) }
    }

}
