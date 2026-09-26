package com.umt.core.media.music

import com.umt.core.media.Genre
import org.hibernate.Hibernate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MusicRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    private lateinit var repository: MusicRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Test
    @DisplayName("findByIdIn returns only the asked-for albums, with their genres already loaded")
    fun findsByIdWithGenres() {
        val rock = entityManager.persist(Genre(name = "Rock"))
        val wanted = entityManager.persist(Music(title = "Wanted", musicbrainzId = "mb-wanted").apply { genres = mutableSetOf(rock) })
        val alsoWanted = entityManager.persist(Music(title = "Also wanted", musicbrainzId = "mb-also"))
        entityManager.persist(Music(title = "Not asked for", musicbrainzId = "mb-other"))
        entityManager.flush()
        entityManager.clear()

        val found = repository.findByIdIn(listOf(wanted.id!!, alsoWanted.id!!))

        assertEquals(setOf("Wanted", "Also wanted"), found.map { it.title }.toSet())
        assertTrue(found.all { Hibernate.isInitialized(it.genres) })
    }
}
