package com.umt.core.contribution

import com.umt.core.media.MediaCategory
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

enum class RoleType { DIRECTOR, DEVELOPER, AUTHOR, ARTIST, WRITER, STUDIO, PUBLISHER }

/**
 * Loosely referenced, on purpose: movie/game/book/album/tv_show are five independent tables,
 * so there's no single table left to put a real FK on. mediaItemId + mediaCategory together say which
 * row in which of the five tables this credits — enforced in application code, not by the DB.
 */
@Entity
@Table(name = Credit.TABLE_NAME)
class Credit(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @field:Column(name = MEDIA_ITEM_ID_COLUMN, nullable = false)
    var mediaItemId: UUID,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = MEDIA_CATEGORY_COLUMN, nullable = false)
    var mediaCategory: MediaCategory,

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
        const val MEDIA_CATEGORY_COLUMN = "media_category"
        const val CONTRIBUTOR_ID_COLUMN = "contributor_id"
        const val ROLE_COLUMN = "role"
    }
}
