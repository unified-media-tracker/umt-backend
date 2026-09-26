package com.umt.core.media.game

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.GameResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.media.game.igdb.IgdbClient
import com.umt.core.media.game.igdb.IgdbGame
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class GameCatalogSyncSchedulerTest {

    private lateinit var igdbClient: IgdbClient
    private lateinit var gameImporter: GameImporter
    private lateinit var scheduler: GameCatalogSyncScheduler

    private val fixedResponse = GameResponse(
        id = UUID.randomUUID(),
        mediaCategory = ApiMediaCategory.GAME,
        title = "assembled",
        releaseDateStatus = ApiReleaseStatus.ANNOUNCED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.IGDB,
        externalSourceId = "1",
    )

    @BeforeEach
    fun setUp() {
        igdbClient = mockk()
        gameImporter = mockk()
        scheduler = GameCatalogSyncScheduler(igdbClient, gameImporter)
    }

    private fun game(id: Long) = IgdbGame(id = id, name = "Game $id", firstReleaseDate = null, summary = null, cover = null)

    @Test
    fun `a game that blows up is skipped without failing the rest of the run`() {
        every { igdbClient.fetchUpcomingGames() } returns listOf(game(1), game(2))
        every { gameImporter.importGame(game(1)) } throws RuntimeException("boom")
        every { gameImporter.importGame(game(2)) } returns fixedResponse

        val result = scheduler.syncUpcomingGames()

        assertEquals(1, result.size)
    }
}
