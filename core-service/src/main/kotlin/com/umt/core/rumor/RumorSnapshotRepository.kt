package com.umt.core.rumor

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RumorSnapshotRepository :
    JpaRepository<RumorSnapshot, UUID> {
    fun findByMediaItemIdOrderByComputedAtDesc(mediaItemId: UUID): List<RumorSnapshot>
}