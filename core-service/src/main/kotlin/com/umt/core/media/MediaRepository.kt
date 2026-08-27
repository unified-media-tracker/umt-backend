package com.umt.core.media

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface MediaRepository : JpaRepository<MediaItem, UUID> {
    // @EntityGraph eagerly joins genres so callers outside a transaction (the non-@Transactional
    // sync loops) can safely map the result to a response — without it, touching the lazy
    // genres collection after the loading transaction closed throws LazyInitializationException.
    @EntityGraph(attributePaths = ["genres"])
    fun findByExternalSourceAndExternalSourceId(
        externalSource: ExternalSourceType,
        externalSourceId: String,
    ): MediaItem?

    @EntityGraph(attributePaths = ["genres"])
    fun findByMediaTypeAndTitleIgnoreCase(
        mediaType: MediaType,
        title: String,
    ): List<MediaItem>

    @Query("""
        SELECT m FROM MediaItem m
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun fndRandomMediaItemsLimit(limit: Int): List<MediaItem>

    fun findByReleaseDateLessThanEqualAndReleaseDateStatusNot(
        date: LocalDate,
        status: ReleaseStatus,
    ): List<MediaItem>
}
