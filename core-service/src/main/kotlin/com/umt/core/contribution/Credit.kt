package com.umt.core.contribution

import com.umt.core.media.MediaItem
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

enum class RoleType { DIRECTOR, DEVELOPER, AUTHOR, ARTIST, WRITER, STUDIO, PUBLISHER }

@Entity
@Table(name = Credit.TABLE_NAME)
class Credit(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = MEDIA_ITEM_ID_COLUMN, nullable = false)
    var mediaItem: MediaItem,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = CONTRIBUTOR_ID_COLUMN, nullable = false)
    var contributor: Contributor,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = ROLE_COLUMN, nullable = false)
    var role: RoleType,
) {
    companion object {
        const val TABLE_NAME = "credit"
        const val ID_COLUMN = "id"
        const val MEDIA_ITEM_ID_COLUMN = "media_item_id"
        const val CONTRIBUTOR_ID_COLUMN = "contributor_id"
        const val ROLE_COLUMN = "role"
    }
}