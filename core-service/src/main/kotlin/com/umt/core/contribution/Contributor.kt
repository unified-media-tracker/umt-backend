package com.umt.core.contribution

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

enum class ContributorType { PERSON, ORGANIZATION }

@Entity
@Table(name = Contributor.TABLE_NAME)
class Contributor(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = CONTRIBUTOR_TYPE_COLUMN, nullable = false)
    var contributorType: ContributorType,

    @field:Column(name = NAME_COLUMN, nullable = false, length = 255)
    var name: String,

    @field:Column(name = DESCRIPTION_COLUMN)
    var description: String? = null,

    @field:Column(name = IMAGE_URL_COLUMN)
    var imageUrl: String? = null,
) {
    companion object {
        const val TABLE_NAME = "contributor"
        const val ID_COLUMN = "id"
        const val CONTRIBUTOR_TYPE_COLUMN = "contributor_type"
        const val NAME_COLUMN = "name"
        const val DESCRIPTION_COLUMN = "description"
        const val IMAGE_URL_COLUMN = "image_url"
    }
}