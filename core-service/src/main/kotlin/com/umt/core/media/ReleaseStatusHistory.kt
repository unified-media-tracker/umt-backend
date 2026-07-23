package com.umt.core.media

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = ReleaseStatusHistory.TABLE_NAME)
class ReleaseStatusHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = MEDIA_ITEM_ID_COLUMN, nullable = false)
    var mediaItem: MediaItem,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = STATUS_COLUMN, nullable = false)
    var status: ReleaseStatus,

    @field:Column(name = CHANGED_AT_COLUMN, nullable = false)
    var changedAt: Instant,

    @field:Column(name = SOURCE_NOTE_COLUMN)
    var sourceNote: String? = null,
) {
    companion object {
        const val TABLE_NAME = "release_status_history"
        const val ID_COLUMN = "id"
        const val MEDIA_ITEM_ID_COLUMN = "media_item_id"
        const val STATUS_COLUMN = "status"
        const val CHANGED_AT_COLUMN = "changed_at"
        const val SOURCE_NOTE_COLUMN = "source_note"
    }
}