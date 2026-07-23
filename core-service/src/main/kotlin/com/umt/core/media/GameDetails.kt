package com.umt.core.media

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = GameDetails.TABLE_NAME)
class GameDetails(
    @OneToOne(optional = false, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @MapsId
    @JoinColumn(name = MEDIA_ITEM_ID_COLUMN)
    var mediaItem: MediaItem,
) {
    @Id
    @field:Column(name = MEDIA_ITEM_ID_COLUMN)
    var id: UUID? = null

    @ManyToMany
    @JoinTable(
        name = "game_platform",
        joinColumns = [JoinColumn(name = "media_item_id")],
        inverseJoinColumns = [JoinColumn(name = "platform_id")],
    )
    var platforms: MutableSet<Platform> = mutableSetOf()

    companion object {
        const val TABLE_NAME = "game_details"
        const val MEDIA_ITEM_ID_COLUMN = "media_item_id"
    }
}