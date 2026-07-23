package com.umt.core.media

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MediaItemRepository : JpaRepository<MediaItem, UUID> {
    fun findByExternalSourceAndExternalSourceId(
        externalSource: ExternalSourceType,
        externalSourceId: String,
    ): MediaItem?
}
