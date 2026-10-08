package com.umt.core.media.tvshow

import com.umt.core.media.ReleaseStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface TvShowRepository : JpaRepository<TvShow, UUID> {
    @EntityGraph(attributePaths = ["genres"])
    override fun findById(id: UUID): Optional<TvShow>

    @EntityGraph(attributePaths = ["genres"])
    fun findByTmdbId(tmdbId: String): TvShow?

    // Batched resolution of TMDb recommendation ids against our own catalog - one query
    // instead of one per candidate id.
    @EntityGraph(attributePaths = ["genres"])
    fun findByTmdbIdIn(tmdbIds: Collection<String>): List<TvShow>

    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(): List<TvShow>

    @EntityGraph(attributePaths = ["genres"])
    fun findByReleaseDateStatus(releaseDateStatus: ReleaseStatus): List<TvShow>

    fun findByReleaseDateLessThanEqualAndReleaseDateStatusNot(date: LocalDate, status: ReleaseStatus): List<TvShow>
}
