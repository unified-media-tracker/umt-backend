package com.umt.core.media.game

import com.umt.core.media.ReleaseStatus
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface GameRepository : JpaRepository<Game, UUID>, JpaSpecificationExecutor<Game> {
    @EntityGraph(attributePaths = ["genres"])
    override fun findById(id: UUID): Optional<Game>

    @EntityGraph(attributePaths = ["genres"])
    fun findByIgdbId(igdbId: String): Game?

    // Batched resolution of IGDB similar_games ids against our own catalog - one query
    // instead of one per candidate id.
    @EntityGraph(attributePaths = ["genres"])
    fun findByIgdbIdIn(igdbIds: Collection<String>): List<Game>

    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(): List<Game>

    // Listing filters (status, release-date window) compose as a Specification - see
    // MediaSpecifications. The entity graph is the same genres fetch as findAll() above.
    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(spec: Specification<Game>?): List<Game>

    fun findByReleaseDateLessThanEqualAndReleaseDateStatusNot(date: LocalDate, status: ReleaseStatus): List<Game>
}
