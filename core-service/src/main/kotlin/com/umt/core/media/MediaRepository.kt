package com.umt.core.media

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface MediaRepository : JpaRepository<MediaItem, UUID> {
    fun findByExternalSourceAndExternalSourceId(
        externalSource: ExternalSourceType,
        externalSourceId: String,
    ): MediaItem?

    // Lets the album sync skip a MusicBrainz call entirely for anything it already
    // imported on a previous run, instead of re-resolving the same ~170 overlapping
    // candidates every day just to throw most of the results away.
    fun existsByMediaTypeAndTitleIgnoreCaseAndReleaseDate(
        mediaType: MediaType,
        title: String,
        releaseDate: LocalDate,
    ): Boolean


    @Query("""
        SELECT m FROM MediaItem m
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun fndRandomMediaItemsLimit(limit: Int): List<MediaItem>
}
