package com.umt.core.media.game

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.game.igdb.IgdbClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class GameCatalogSyncScheduler(
    private val igdbClient: IgdbClient,
    private val gameImporter: GameImporter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 4 * * *")
    fun syncUpcomingGames(): List<MediaItemResponse> {
        log.info("Starting scheduled upcoming-games sync")
        val games = igdbClient.fetchUpcomingGames()
        val results = mutableListOf<MediaItemResponse>()

        for (game in games) {
            try {
                results.add(gameImporter.importGame(game))
            } catch (ex: Exception) {
                log.error("Failed to process IGDB game {} - {}, skipping it this run", game.id, game.name, ex)
            }
        }

        log.info("Game sync: {} fetched from IGDB", games.size)
        return results
    }
}
