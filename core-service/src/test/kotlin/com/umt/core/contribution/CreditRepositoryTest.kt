package com.umt.core.contribution

import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaCategory
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CreditRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    private lateinit var repository: CreditRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private fun contributor(source: ExternalSourceType, externalId: String) =
        entityManager.persist(
            Contributor(contributorType = ContributorType.PERSON, name = "Contributor $externalId", externalSource = source, externalSourceId = externalId)
        )

    private fun credit(itemId: UUID, category: MediaCategory, contributor: Contributor, role: RoleType) =
        entityManager.persist(Credit(mediaItemId = itemId, mediaCategory = category, contributor = contributor, role = role))

    private fun findAlbumsByArtists(vararg externalIds: String) =
        repository.findCreditedItems(MediaCategory.MUSIC, RoleType.ARTIST, ExternalSourceType.MUSICBRAINZ, externalIds.toList())

    @Test
    @DisplayName("findCreditedItems returns every item credited to the given contributors, with the contributor's external id")
    fun findsItemsForEachContributor() {
        val artistA = contributor(ExternalSourceType.MUSICBRAINZ, "mb-a")
        val artistB = contributor(ExternalSourceType.MUSICBRAINZ, "mb-b")
        val albumA = UUID.randomUUID()
        val firstByB = UUID.randomUUID()
        val secondByB = UUID.randomUUID()
        credit(albumA, MediaCategory.MUSIC, artistA, RoleType.ARTIST)
        credit(firstByB, MediaCategory.MUSIC, artistB, RoleType.ARTIST)
        credit(secondByB, MediaCategory.MUSIC, artistB, RoleType.ARTIST)
        entityManager.flush()
        entityManager.clear()

        val found = findAlbumsByArtists("mb-a", "mb-b")

        assertEquals(
            setOf("mb-a" to albumA, "mb-b" to firstByB, "mb-b" to secondByB),
            found.map { it.externalId to it.mediaItemId }.toSet(),
        )
    }

    @Test
    @DisplayName("findCreditedItems skips other roles, categories, sources and contributors")
    fun skipsEverythingElse() {
        val artist = contributor(ExternalSourceType.MUSICBRAINZ, "mb-a")
        val sameIdInAnotherSource = contributor(ExternalSourceType.TMDB, "mb-a")
        val notAskedAbout = contributor(ExternalSourceType.MUSICBRAINZ, "mb-other")
        val wanted = UUID.randomUUID()
        credit(wanted, MediaCategory.MUSIC, artist, RoleType.ARTIST)
        credit(UUID.randomUUID(), MediaCategory.MUSIC, artist, RoleType.WRITER)
        credit(UUID.randomUUID(), MediaCategory.MOVIE, artist, RoleType.ARTIST)
        credit(UUID.randomUUID(), MediaCategory.MUSIC, sameIdInAnotherSource, RoleType.ARTIST)
        credit(UUID.randomUUID(), MediaCategory.MUSIC, notAskedAbout, RoleType.ARTIST)
        entityManager.flush()
        entityManager.clear()

        val found = findAlbumsByArtists("mb-a")

        assertEquals(listOf(wanted), found.map { it.mediaItemId })
    }

    @Test
    @DisplayName("findCreditedItems with no external ids finds nothing")
    fun emptyIdList() {
        credit(UUID.randomUUID(), MediaCategory.MUSIC, contributor(ExternalSourceType.MUSICBRAINZ, "mb-a"), RoleType.ARTIST)
        entityManager.flush()

        assertEquals(emptyList<CreditedItem>(), findAlbumsByArtists())
    }
}
