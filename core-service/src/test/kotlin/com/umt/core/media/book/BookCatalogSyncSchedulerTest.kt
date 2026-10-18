package com.umt.core.media.book

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.media.book.hardcover.HardcoverBook
import com.umt.core.media.book.hardcover.HardcoverClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class BookCatalogSyncSchedulerTest {

    private lateinit var hardcoverClient: HardcoverClient
    private lateinit var bookImporter: BookImporter
    private lateinit var scheduler: BookCatalogSyncScheduler

    private val fixedResponse = MediaItemResponse(
        id = UUID.randomUUID(),
        mediaCategory = ApiMediaCategory.BOOK,
        title = "assembled",
        releaseDateStatus = ApiReleaseStatus.ANNOUNCED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.HARDCOVER,
        externalSourceId = "1",
    )

    @BeforeEach
    fun setUp() {
        hardcoverClient = mockk()
        bookImporter = mockk()
        scheduler = BookCatalogSyncScheduler(hardcoverClient, bookImporter)
    }

    private fun book(id: Long) = HardcoverBook(
        id = id, title = "Book $id", description = null, slug = "book-$id",
        releaseDate = "2027-01-01", image = null, contributions = emptyList(),
    )

    @Test
    fun `imports every fetched book and returns the results`() {
        every { hardcoverClient.fetchUpcomingBooks() } returns listOf(book(1), book(2))
        every { bookImporter.importBook(book(1)) } returns fixedResponse
        every { bookImporter.importBook(book(2)) } returns fixedResponse

        val result = scheduler.syncUpcomingBooks()

        assertEquals(2, result.size)
        verify(exactly = 1) { bookImporter.importBook(book(1)) }
        verify(exactly = 1) { bookImporter.importBook(book(2)) }
    }

    @Test
    fun `a book that blows up is skipped without failing the rest of the run`() {
        every { hardcoverClient.fetchUpcomingBooks() } returns listOf(book(1), book(2))
        every { bookImporter.importBook(book(1)) } throws RuntimeException("boom")
        every { bookImporter.importBook(book(2)) } returns fixedResponse

        val result = scheduler.syncUpcomingBooks()

        assertEquals(1, result.size)
    }
}
