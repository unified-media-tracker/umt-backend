package com.umt.core.media.movie

import com.umt.core.media.ReleaseStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface MovieRepository : JpaRepository<Movie, UUID> {
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

    @EntityGraph(attributePaths = ["genres"])
    fun findByReleaseDateStatus(releaseDateStatus: ReleaseStatus): List<Movie>

    fun findByReleaseDateLessThanEqualAndReleaseDateStatusNot(date: LocalDate, status: ReleaseStatus): List<Movie>
}
