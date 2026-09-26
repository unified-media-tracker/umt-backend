package com.umt.core.rumor

import com.umt.core.media.MediaCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RumorSnapshotRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    private lateinit var repository: RumorSnapshotRepository

    private fun snapshot(mediaItemId: UUID, computedAt: String) {
        repository.save(
            RumorSnapshot(
                mediaItemId = mediaItemId,
                mediaCategory = MediaCategory.MOVIE,
                delayProbability = BigDecimal("5.00"),
                computedAt = Instant.parse(computedAt),
            )
        )
    }

    @Test
    @DisplayName("findLatestComputedAt returns each item's newest snapshot time, and nothing for an item never scored")
    fun latestComputedAtPerItem() {
        val twice = UUID.randomUUID()
        val once = UUID.randomUUID()
        val never = UUID.randomUUID()
        snapshot(twice, "2026-09-20T06:00:00Z")
        snapshot(twice, "2026-09-24T06:00:00Z")
        snapshot(twice, "2026-09-22T06:00:00Z")
        snapshot(once, "2026-09-21T06:00:00Z")

        val latest = repository.findLatestComputedAt(listOf(twice, once, never)).associate { it.mediaItemId to it.computedAt }

        assertEquals(
            mapOf(twice to Instant.parse("2026-09-24T06:00:00Z"), once to Instant.parse("2026-09-21T06:00:00Z")),
            latest,
        )
    }

    @Test
    @DisplayName("findLatestComputedAt only looks at the ids it is asked about")
    fun latestComputedAtIgnoresOtherItems() {
        val asked = UUID.randomUUID()
        snapshot(asked, "2026-09-24T06:00:00Z")
        snapshot(UUID.randomUUID(), "2026-09-25T06:00:00Z")

        assertEquals(listOf(asked), repository.findLatestComputedAt(listOf(asked)).map { it.mediaItemId })
    }
}
