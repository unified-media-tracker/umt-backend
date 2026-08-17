package com.umt.core.contribution

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CreditRepository : JpaRepository<Credit, UUID> {
    // MediaItem has no inverse "credits" collection - Credit only points at MediaItem, not
    // the other way round - so this is a fresh, explicit query rather than a lazy relationship.
    @EntityGraph(attributePaths = ["contributor"])
    fun findByMediaItemId(mediaItemId: UUID): List<Credit>
}
