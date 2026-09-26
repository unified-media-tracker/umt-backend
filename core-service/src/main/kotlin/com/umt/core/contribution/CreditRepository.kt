package com.umt.core.contribution

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CreditRepository : JpaRepository<Credit, UUID> {
    @EntityGraph(attributePaths = ["contributor"])
    fun findByMediaItemId(mediaItemId: UUID): List<Credit>

    @EntityGraph(attributePaths = ["contributor"])
    fun findByMediaItemIdIn(mediaItemIds: Collection<UUID>): List<Credit>
}
