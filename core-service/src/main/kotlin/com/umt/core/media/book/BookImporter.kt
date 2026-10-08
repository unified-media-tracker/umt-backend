package com.umt.core.media.book

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.contribution.RoleType
import com.umt.core.media.ContributorCreditService
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaResponseAssembler
import com.umt.core.media.ReleaseDateSyncService
import com.umt.core.media.book.hardcover.HardcoverBook
import com.umt.core.media.book.hardcover.parsedReleaseDate
import com.umt.core.media.book.hardcover.primaryAuthor
import com.umt.core.media.book.hardcover.toBook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BookImporter(
    private val bookRepository: BookRepository,
    private val mediaResponseAssembler: MediaResponseAssembler,
    private val mediaEventPublisher: MediaEventPublisher,
    private val releaseDateSyncService: ReleaseDateSyncService,
    private val contributorCreditService: ContributorCreditService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun importBook(hardcoverBook: HardcoverBook): MediaItemResponse {
        val existing = bookRepository.findByHardcoverId(hardcoverBook.id.toString())
        if (existing != null) {
            val updated = releaseDateSyncService.updateIfChanged(existing, hardcoverBook.parsedReleaseDate, "Hardcover")
            return mediaResponseAssembler.assemble(updated)
        }

        val saved = bookRepository.save(hardcoverBook.toBook())

        val author = hardcoverBook.primaryAuthor
        if (author == null) {
            log.warn("Hardcover book {} had no linked author, skipping credit", hardcoverBook.id)
        } else {
            contributorCreditService.credit(saved, ExternalSourceType.HARDCOVER, author.id.toString(), author.name, RoleType.AUTHOR)
        }
        mediaEventPublisher.publishIfUpcoming(saved)

        return mediaResponseAssembler.assemble(saved)
    }
}
