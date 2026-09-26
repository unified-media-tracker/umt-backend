package com.umt.core.rumor

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

/**
 *  When an item was last scored - what a MediaAnalysisScheduler run ranks by.
 */
interface LatestAnalysis {
    val mediaItemId: UUID
    val computedAt: Instant
}

interface RumorSnapshotRepository :
    JpaRepository<RumorSnapshot, UUID> {
    fun findByMediaItemIdOrderByComputedAtDesc(mediaItemId: UUID): List<RumorSnapshot>

    // Batched form for assembling a whole list response in one query instead of N.
    fun findByMediaItemIdIn(mediaItemIds: Collection<UUID>): List<RumorSnapshot>

    // Items never scored simply have no row here.
    @Query(
        "select s.mediaItemId as mediaItemId, max(s.computedAt) as computedAt " +
            "from RumorSnapshot s where s.mediaItemId in :mediaItemIds group by s.mediaItemId"
    )
    fun findLatestComputedAt(mediaItemIds: Collection<UUID>): List<LatestAnalysis>
}
