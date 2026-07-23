package com.umt.core.media.genre

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GenreRepository : JpaRepository<Genre, UUID> {
    fun findByName(name: String): Genre?
}