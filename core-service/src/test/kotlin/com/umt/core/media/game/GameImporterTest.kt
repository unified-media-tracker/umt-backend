package com.umt.core.media.game

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.GameResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.contribution.ContributorType
import com.umt.core.contribution.RoleType
import com.umt.core.media.game.igdb.IgdbCompany
import com.umt.core.media.game.igdb.IgdbGame
import com.umt.core.media.game.igdb.IgdbInvolvedCompany
import com.umt.core.media.ContributorCreditService
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaResponseAssembler
import com.umt.core.media.ReleaseDateSyncService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.LocalDate
import java.util.UUID

class GameImporterTest {

    private lateinit var gameRepository: GameRepository
    private lateinit var mediaResponseAssembler: MediaResponseAssembler
    private lateinit var mediaEventPublisher: MediaEventPublisher
    private lateinit var releaseDateSyncService: ReleaseDateSyncService
    private lateinit var contributorCreditService: ContributorCreditService
    private lateinit var importer: GameImporter

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
        gameRepository = mockk()
        mediaResponseAssembler = mockk()
        mediaEventPublisher = mockk()
        releaseDateSyncService = mockk()
        contributorCreditService = mockk()
        importer = GameImporter(gameRepository, mediaResponseAssembler, mediaEventPublisher, releaseDateSyncService, contributorCreditService)

        every { gameRepository.save(any<Game>()) } answers { firstArg<Game>() }
        every { mediaEventPublisher.requestAnalysisIfUpcoming(any()) } returns Unit
        every { contributorCreditService.credit(any<Game>(), any(), any(), any(), any(), any()) } returns Unit
        every { mediaResponseAssembler.assemble(any<Game>()) } returns fixedResponse
    }

    private fun existingGame(igdbId: String) = Game(id = UUID.randomUUID(), title = "Old title", igdbId = igdbId)

    private fun igdbGame(id: Long = 1L, companies: List<IgdbInvolvedCompany> = emptyList()) = IgdbGame(
        id = id,
        name = "Half-Life 3",
        firstReleaseDate = LocalDate.of(2027, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
        summary = "At last",
        cover = null,
        involvedCompanies = companies,
    )

    @Test
    fun `an already-known game is re-synced, not re-credited`() {
        val existing = existingGame("1")
        every { gameRepository.findByIgdbId("1") } returns existing
        every { releaseDateSyncService.updateIfChanged(existing, any(), "IGDB") } returns existing

        val result = importer.importGame(igdbGame())

        assertEquals(fixedResponse, result)
        verify(exactly = 0) { gameRepository.save(any()) }
        verify(exactly = 0) { contributorCreditService.credit(any<Game>(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a new game credits its developer and its publisher separately`() {
        every { gameRepository.findByIgdbId("1") } returns null
        val studio = IgdbCompany(id = 50, name = "Valve")
        val involved = IgdbInvolvedCompany(company = studio, developer = true, publisher = true)

        val result = importer.importGame(igdbGame(companies = listOf(involved)))

        assertEquals(fixedResponse, result)
        verify(exactly = 1) {
            contributorCreditService.credit(any<Game>(), ExternalSourceType.IGDB, "50", "Valve", RoleType.DEVELOPER, ContributorType.ORGANIZATION)
        }
        verify(exactly = 1) {
            contributorCreditService.credit(any<Game>(), ExternalSourceType.IGDB, "50", "Valve", RoleType.PUBLISHER, ContributorType.ORGANIZATION)
        }
        verify(exactly = 1) { mediaEventPublisher.requestAnalysisIfUpcoming(any()) }
    }

    @Test
    fun `an involved company with no company reference is skipped without crediting or crashing`() {
        every { gameRepository.findByIgdbId("1") } returns null
        val involved = IgdbInvolvedCompany(company = null, developer = true, publisher = true)

        val result = importer.importGame(igdbGame(companies = listOf(involved)))

        assertEquals(fixedResponse, result)
        verify(exactly = 0) { contributorCreditService.credit(any<Game>(), any(), any(), any(), any(), any()) }
    }
}
