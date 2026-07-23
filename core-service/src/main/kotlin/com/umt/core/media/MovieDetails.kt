package com.umt.core.media

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = MovieDetails.TABLE_NAME)
class MovieDetails(
    @OneToOne(optional = false, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @MapsId
    @JoinColumn(name = MEDIA_ITEM_ID_COLUMN)
    var mediaItem: MediaItem,

    @field:Column(name = RUNTIME_MINUTES_COLUMN)
    var runtimeMinutes: Int? = null,
) {

    // The @Id field MUST be nullable when using @MapsId for new inserts,
    // and it shouldn't be in the primary constructor to avoid manual setting.
    @Id
    @field:Column(name = MEDIA_ITEM_ID_COLUMN)
    var id: UUID? = null

    companion object {
        const val TABLE_NAME = "movie_details"
        const val MEDIA_ITEM_ID_COLUMN = "media_item_id"
        const val RUNTIME_MINUTES_COLUMN = "runtime_minutes"
    }
}