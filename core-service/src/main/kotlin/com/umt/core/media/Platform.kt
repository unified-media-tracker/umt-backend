package com.umt.core.media

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = Platform.TABLE_NAME)
class Platform(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @field:Column(name = NAME_COLUMN, nullable = false, unique = true, length = 50)
    var name: String,
) {
    companion object {
        const val TABLE_NAME = "platform"
        const val ID_COLUMN = "id"
        const val NAME_COLUMN = "name"
    }
}