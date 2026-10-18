package com.umt.core.media.movie

import com.umt.core.media.ReleaseStatus
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface MovieRepository : JpaRepository<Movie, UUID>, JpaSpecificationExecutor<Movie> {
    // findById overridden purely to attach @EntityGraph - see MediaResponseAssembler's LazyInitializationException
    // history for why a plain findById() here would break the first time someone reads .genres.
    @EntityGraph(attributePaths = ["genres"])
    override fun findById(id: UUID): Optional<Movie>

    @EntityGraph(attributePaths = ["genres"])
    fun findByTmdbId(tmdbId: String): Movie?

    // Batched resolution of TMDb recommendation ids against our own catalog - one query
    // instead of one per candidate id.
    @EntityGraph(attributePaths = ["genres"])
    fun findByTmdbIdIn(tmdbIds: Collection<String>): List<Movie>

    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(): List<Movie>

    // Listing filters (status, release-date window) compose as a Specification - see
    // MediaSpecifications. The entity graph is the same genres fetch as findAll() above.
    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(spec: Specification<Movie>?): List<Movie>

    fun findByReleaseDateLessThanEqualAndReleaseDateStatusNot(date: LocalDate, status: ReleaseStatus): List<Movie>
}
