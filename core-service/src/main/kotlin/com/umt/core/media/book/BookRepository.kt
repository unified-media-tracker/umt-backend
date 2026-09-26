package com.umt.core.media.book

import com.umt.core.media.ReleaseStatus
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface BookRepository : JpaRepository<Book, UUID>, JpaSpecificationExecutor<Book> {
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

    // Listing filters (status, release-date window) compose as a Specification - see
    // MediaSpecifications. The entity graph is the same genres fetch as findAll() above.
    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(spec: Specification<Book>?): List<Book>

    fun findByReleaseDateLessThanEqualAndReleaseDateStatusNot(date: LocalDate, status: ReleaseStatus): List<Book>
}
