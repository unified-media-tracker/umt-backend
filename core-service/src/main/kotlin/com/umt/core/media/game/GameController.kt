package com.umt.core.media.game

import com.umt.api.generated.GameApi
import com.umt.api.generated.model.GameResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class GameController(
    private val gameCatalogSyncScheduler: GameCatalogSyncScheduler,
) : GameApi {

    @PreAuthorize("hasRole('ADMIN')")
    override fun syncUpcomingGames(): ResponseEntity<List<GameResponse>> =
        ResponseEntity.ok(gameCatalogSyncScheduler.syncUpcomingGames())
}
