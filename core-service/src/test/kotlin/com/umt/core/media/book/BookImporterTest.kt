package com.umt.core.media.book

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.media.book.hardcover.HardcoverAuthor
import com.umt.core.media.book.hardcover.HardcoverBook
import com.umt.core.media.book.hardcover.HardcoverContribution
import com.umt.core.contribution.RoleType
import com.umt.core.media.ContributorCreditService
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaResponseAssembler
import com.umt.core.media.ReleaseDateSyncService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class BookImporterTest {

    private lateinit var bookRepository: BookRepository
    private lateinit var mediaResponseAssembler: MediaResponseAssembler
    private lateinit var mediaEventPublisher: MediaEventPublisher
    private lateinit var releaseDateSyncService: ReleaseDateSyncService
    private lateinit var contributorCreditService: ContributorCreditService
    private lateinit var importer: BookImporter

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
        bookRepository = mockk()
        mediaResponseAssembler = mockk()
        mediaEventPublisher = mockk()
        releaseDateSyncService = mockk()
        contributorCreditService = mockk()
        importer = BookImporter(bookRepository, mediaResponseAssembler, mediaEventPublisher, releaseDateSyncService, contributorCreditService)

        every { bookRepository.save(any<Book>()) } answers { firstArg<Book>() }
        every { mediaEventPublisher.publishIfUpcoming(any()) } returns Unit
        every { contributorCreditService.credit(any<Book>(), any(), any(), any(), any(), any()) } returns Unit
        every { mediaResponseAssembler.assemble(any<Book>()) } returns fixedResponse
    }

    private fun existingBook(hardcoverId: String) = Book(id = UUID.randomUUID(), title = "Old title", hardcoverId = hardcoverId)

    private fun hardcoverBook(id: Long = 1L, contributions: List<HardcoverContribution> = emptyList()) = HardcoverBook(
        id = id, title = "Dune", description = null, slug = "dune",
        releaseDate = "2027-01-01", image = null, contributions = contributions,
    )

    @Test
    fun `an already-known book is re-synced, not re-credited`() {
        val existing = existingBook("1")
        every { bookRepository.findByHardcoverId("1") } returns existing
        every { releaseDateSyncService.updateIfChanged(existing, any(), "Hardcover") } returns existing

        val result = importer.importBook(hardcoverBook())

        assertEquals(fixedResponse, result)
        verify(exactly = 0) { bookRepository.save(any()) }
        verify(exactly = 0) { contributorCreditService.credit(any<Book>(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a new book with a primary author gets it credited as AUTHOR`() {
        every { bookRepository.findByHardcoverId("1") } returns null
        val author = HardcoverAuthor(id = 7, name = "Frank Herbert")

        val result = importer.importBook(hardcoverBook(contributions = listOf(HardcoverContribution(author = author, contribution = null))))

        assertEquals(fixedResponse, result)
        verify(exactly = 1) {
            contributorCreditService.credit(any<Book>(), ExternalSourceType.HARDCOVER, "7", "Frank Herbert", RoleType.AUTHOR, any())
        }
        verify(exactly = 1) { mediaEventPublisher.publishIfUpcoming(any()) }
    }

    @Test
    fun `a new book with no linked author is still saved, just not credited`() {
        every { bookRepository.findByHardcoverId("1") } returns null

        val result = importer.importBook(hardcoverBook(contributions = emptyList()))

        assertEquals(fixedResponse, result)
        verify(exactly = 0) { contributorCreditService.credit(any<Book>(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { mediaEventPublisher.publishIfUpcoming(any()) }
    }
}
