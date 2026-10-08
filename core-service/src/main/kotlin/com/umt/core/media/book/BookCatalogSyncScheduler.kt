package com.umt.core.media.book

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.book.hardcover.HardcoverClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BookCatalogSyncScheduler(
    private val hardcoverClient: HardcoverClient,
    private val bookImporter: BookImporter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 5 * * *")
    fun syncUpcomingBooks(): List<MediaItemResponse> {
        log.info("Starting scheduled upcoming-books sync")
        val books = hardcoverClient.fetchUpcomingBooks()
        val results = mutableListOf<MediaItemResponse>()

        for (book in books) {
            try {
                results.add(bookImporter.importBook(book))
            } catch (ex: Exception) {
                log.error("Failed to process Hardcover book {} - {}, skipping it this run", book.id, book.title, ex)
            }
        }

        log.info("Book sync: {} fetched from Hardcover", books.size)
        return results
    }
}
