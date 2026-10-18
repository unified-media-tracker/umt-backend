package com.umt.core.contribution

import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaCategory
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** A media item credited to a contributor, with that contributor's id in its source catalogue. */
interface CreditedItem {
    val externalId: String
    val mediaItemId: UUID
}

interface CreditRepository : JpaRepository<Credit, UUID> {
    @EntityGraph(attributePaths = ["contributor"])
    fun findByMediaItemId(mediaItemId: UUID): List<Credit>

    @EntityGraph(attributePaths = ["contributor"])
    fun findByMediaItemIdIn(mediaItemIds: Collection<UUID>): List<Credit>

    @Query(
        "select ct.externalSourceId as externalId, c.mediaItemId as mediaItemId " +
            "from Credit c join c.contributor ct " +
            "where c.mediaCategory = :mediaCategory and c.role = :role " +
            "and ct.externalSource = :source and ct.externalSourceId in :externalIds"
    )
    fun findCreditedItems(
        mediaCategory: MediaCategory,
        role: RoleType,
        source: ExternalSourceType,
        externalIds: Collection<String>,
    ): List<CreditedItem>
}
