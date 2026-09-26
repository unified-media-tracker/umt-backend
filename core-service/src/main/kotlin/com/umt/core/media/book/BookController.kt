package com.umt.core.media.book

import com.umt.api.generated.BookApi
import com.umt.api.generated.model.BookResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class BookController(
    private val bookCatalogSyncScheduler: BookCatalogSyncScheduler,
) : BookApi {

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingBooks(): ResponseEntity<List<BookResponse>> =
        ResponseEntity.ok(bookCatalogSyncScheduler.syncUpcomingBooks())
}
