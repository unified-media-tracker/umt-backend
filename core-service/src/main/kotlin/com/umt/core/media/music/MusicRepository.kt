package com.umt.core.media.music

import com.umt.core.media.ReleaseStatus
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface MusicRepository : JpaRepository<Music, UUID>, JpaSpecificationExecutor<Music> {
    @EntityGraph(attributePaths = ["genres"])
    override fun findById(id: UUID): Optional<Music>

    @EntityGraph(attributePaths = ["genres"])
    fun findByMusicbrainzId(musicbrainzId: String): Music?

    @EntityGraph(attributePaths = ["genres"])
    fun findByTitleIgnoreCase(title: String): List<Music>

    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(): List<Music>

    // Listing filters (status, release-date window) compose as a Specification - see
    // MediaSpecifications. The entity graph is the same genres fetch as findAll() above.
    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(spec: Specification<Music>?): List<Music>

    fun findByReleaseDateLessThanEqualAndReleaseDateStatusNot(date: LocalDate, status: ReleaseStatus): List<Music>
}
