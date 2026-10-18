package com.umt.core.media.game

import com.umt.core.media.ReleaseStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface GameRepository : JpaRepository<Game, UUID> {
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

    @EntityGraph(attributePaths = ["genres"])
    fun findByReleaseDateStatus(releaseDateStatus: ReleaseStatus): List<Game>

    fun findByReleaseDateLessThanEqualAndReleaseDateStatusNot(date: LocalDate, status: ReleaseStatus): List<Game>
}
