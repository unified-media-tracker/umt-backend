package com.umt.core.media.genre

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = Genre.TABLE_NAME)
class Genre(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @field:Column(name = NAME_COLUMN, nullable = false, unique = true, length = 50)
    var name: String,
) {
    companion object {
        const val TABLE_NAME = "genre"
        const val ID_COLUMN = "id"
        const val NAME_COLUMN = "name"
    }
}