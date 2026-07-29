package com.umt.core.media

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface MediaRepository : JpaRepository<MediaItem, UUID> {
    fun findByExternalSourceAndExternalSourceId(
        externalSource: ExternalSourceType,
        externalSourceId: String,
    ): MediaItem?


    @Query("""
        SELECT m FROM MediaItem m
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun fndRandomMediaItemsLimit(limit: Int): List<MediaItem>
}
