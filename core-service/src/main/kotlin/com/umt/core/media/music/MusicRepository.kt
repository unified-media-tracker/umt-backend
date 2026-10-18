package com.umt.core.media.music

import com.umt.core.media.ReleaseStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface MusicRepository : JpaRepository<Music, UUID> {
    @EntityGraph(attributePaths = ["genres"])
    override fun findById(id: UUID): Optional<Music>

    @EntityGraph(attributePaths = ["genres"])
    fun findByMusicbrainzId(musicbrainzId: String): Music?

    @EntityGraph(attributePaths = ["genres"])
    fun findByTitleIgnoreCase(title: String): List<Music>

    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(): List<Music>

    @EntityGraph(attributePaths = ["genres"])
    fun findByReleaseDateStatus(releaseDateStatus: ReleaseStatus): List<Music>

    fun findByReleaseDateLessThanEqualAndReleaseDateStatusNot(date: LocalDate, status: ReleaseStatus): List<Music>
}
