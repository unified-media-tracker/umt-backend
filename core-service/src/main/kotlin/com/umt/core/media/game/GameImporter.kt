package com.umt.core.media.game

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.contribution.ContributorType
import com.umt.core.contribution.RoleType
import com.umt.core.media.ContributorCreditService
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaResponseAssembler
import com.umt.core.media.ReleaseDateSyncService
import com.umt.core.media.game.igdb.IgdbGame
import com.umt.core.media.game.igdb.IgdbInvolvedCompany
import com.umt.core.media.game.igdb.parsedReleaseDate
import com.umt.core.media.game.igdb.toGame
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GameImporter(
    private val gameRepository: GameRepository,
    private val mediaResponseAssembler: MediaResponseAssembler,
    private val mediaEventPublisher: MediaEventPublisher,
    private val releaseDateSyncService: ReleaseDateSyncService,
    private val contributorCreditService: ContributorCreditService,
) {

    @Transactional
    fun importGame(igdbGame: IgdbGame): MediaItemResponse {
        val existing = gameRepository.findByIgdbId(igdbGame.id.toString())
        if (existing != null) {
            val updated = releaseDateSyncService.updateIfChanged(existing, igdbGame.parsedReleaseDate, "IGDB")
            return mediaResponseAssembler.assemble(updated)
        }

        val saved = gameRepository.save(igdbGame.toGame())
        creditCompanies(saved, igdbGame.involvedCompanies)
        mediaEventPublisher.publishIfUpcoming(saved)

        return mediaResponseAssembler.assemble(saved)
    }

    private fun creditCompanies(game: Game, companies: List<IgdbInvolvedCompany>) {
        companies.forEach { involved ->
            val company = involved.company ?: return@forEach
            if (involved.developer) {
                contributorCreditService.credit(
                    game, ExternalSourceType.IGDB, company.id.toString(), company.name,
                    RoleType.DEVELOPER, ContributorType.ORGANIZATION,
                )
            }
            if (involved.publisher) {
                contributorCreditService.credit(
                    game, ExternalSourceType.IGDB, company.id.toString(), company.name,
                    RoleType.PUBLISHER, ContributorType.ORGANIZATION,
                )
            }
        }
    }
}
