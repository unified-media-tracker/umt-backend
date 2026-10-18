package com.umt.core.media.music.similarity

import org.hibernate.Hibernate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ArtistSimilarityRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    private lateinit var repository: ArtistSimilarityRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val artist = "a74b1b7f-71a5-4011-9441-d0b5e4122711"

    private fun lookup(fetchedAt: String, vararg similar: Triple<String, String, Int>) =
        ArtistSimilarity(artist, Instant.parse(fetchedAt)).apply {
            similarArtists = similar.map { (mbid, name, score) -> SimilarArtist(mbid, name, score) }.toMutableList()
        }

    private fun similarArtistRows() =
        jdbc.queryForList("select similar_artist_mbid from similar_artist where artist_mbid = ?", String::class.java, artist).toSet()

    private fun reload(): ArtistSimilarity {
        entityManager.flush()
        entityManager.clear()
        return repository.findById(artist).orElseThrow()
    }

    @Test
    @DisplayName("a lookup comes back with its similar artists already loaded")
    fun storesAndReadsBack() {
        repository.save(lookup("2026-09-26T12:00:00Z", Triple("b", "Muse", 10817), Triple("a", "Nirvana", 11782)))

        val found = reload()

        assertTrue(Hibernate.isInitialized(found.similarArtists))
        assertEquals(Instant.parse("2026-09-26T12:00:00Z"), found.fetchedAt)
        assertEquals(
            setOf(Triple("a", "Nirvana", 11782), Triple("b", "Muse", 10817)),
            found.similarArtists.map { Triple(it.mbid, it.name, it.score) }.toSet(),
        )
    }

    @Test
    @DisplayName("saving a lookup again replaces the similar artists it had")
    fun replacesSimilarArtists() {
        repository.save(lookup("2026-08-01T12:00:00Z", Triple("old-1", "Old One", 5), Triple("old-2", "Old Two", 4)))
        entityManager.flush()
        entityManager.clear()

        repository.save(lookup("2026-09-26T12:00:00Z", Triple("new", "New", 9)))
        val found = reload()

        assertEquals(Instant.parse("2026-09-26T12:00:00Z"), found.fetchedAt)
        assertEquals(listOf("new"), found.similarArtists.map { it.mbid })
        assertEquals(setOf("new"), similarArtistRows())
    }

    @Test
    @DisplayName("a lookup with no similar artists is kept, so an empty answer is remembered")
    fun storesEmptyLookup() {
        repository.save(lookup("2026-09-26T12:00:00Z"))

        val found = reload()

        assertTrue(found.similarArtists.isEmpty())
        assertTrue(repository.existsById(artist))
    }

    @Test
    @DisplayName("deleting a lookup deletes its similar artists")
    fun deleteCascades() {
        repository.save(lookup("2026-09-26T12:00:00Z", Triple("a", "Nirvana", 11782)))
        entityManager.flush()

        repository.deleteById(artist)
        entityManager.flush()

        assertFalse(repository.existsById(artist))
        assertTrue(similarArtistRows().isEmpty())
    }
}
