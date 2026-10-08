package com.umt.core.media.book

import com.umt.core.media.ReleaseStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface BookRepository : JpaRepository<Book, UUID> {
    @EntityGraph(attributePaths = ["genres"])
    override fun findById(id: UUID): Optional<Book>

    @EntityGraph(attributePaths = ["genres"])
    fun findByHardcoverId(hardcoverId: String): Book?

    // Batched resolution of Hardcover's cached_similar_book_ids against our own catalog -
    // one query instead of one per candidate id.
    @EntityGraph(attributePaths = ["genres"])
    fun findByHardcoverIdIn(hardcoverIds: Collection<String>): List<Book>

    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(): List<Book>

    @EntityGraph(attributePaths = ["genres"])
    fun findByReleaseDateStatus(releaseDateStatus: ReleaseStatus): List<Book>

    fun findByReleaseDateLessThanEqualAndReleaseDateStatusNot(date: LocalDate, status: ReleaseStatus): List<Book>
}
