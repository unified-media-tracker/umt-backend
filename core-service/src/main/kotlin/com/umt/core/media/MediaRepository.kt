package com.umt.core.media

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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

    // Lets the album sync skip a MusicBrainz call for anything it already resolved on a
    // previous run — matched by title only (not title+date), so a date change on an already-known
    // album is still caught here and can be compared/updated without spending a
    // MusicBrainz call just to find the same MBID again.
    @EntityGraph(attributePaths = ["genres"])
    fun findFirstByMediaTypeAndTitleIgnoreCase(
        mediaType: MediaType,
        title: String,
    ): MediaItem?

    @Query("""
        SELECT m FROM MediaItem m
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun fndRandomMediaItemsLimit(limit: Int): List<MediaItem>
}
