package com.umt.core.media

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

enum class TagType { MOOD, SETTING, KEYWORD }

@Entity
@Table(
    name = Tag.TABLE_NAME,
    uniqueConstraints = [UniqueConstraint(columnNames = [Tag.NAME_COLUMN, Tag.TAG_TYPE_COLUMN])],
)
class Tag(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @field:Column(name = NAME_COLUMN, nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = TAG_TYPE_COLUMN, nullable = false)
    var tagType: TagType,
) {
    companion object {
        const val TABLE_NAME = "tag"
        const val ID_COLUMN = "id"
        const val NAME_COLUMN = "name"
        const val TAG_TYPE_COLUMN = "tag_type"
    }
}