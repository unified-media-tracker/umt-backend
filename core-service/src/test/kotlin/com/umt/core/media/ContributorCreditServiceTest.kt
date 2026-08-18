package com.umt.core.media

import com.umt.core.contribution.Contributor
import com.umt.core.contribution.ContributorRepository
import com.umt.core.contribution.ContributorType
import com.umt.core.contribution.Credit
import com.umt.core.contribution.CreditRepository
import com.umt.core.contribution.RoleType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class ContributorCreditServiceTest {

    private lateinit var contributorRepository: ContributorRepository
    private lateinit var creditRepository: CreditRepository
    private lateinit var service: ContributorCreditService

    private val mediaItem = MediaItem(
        id = UUID.randomUUID(),
        mediaType = MediaType.MOVIE,
        title = "Inception",
        externalSource = ExternalSourceType.TMDB,
        externalSourceId = "27205",
    )

    @BeforeEach
    fun setUp() {
        contributorRepository = mockk()
        creditRepository = mockk()
        service = ContributorCreditService(contributorRepository, creditRepository)
        every { creditRepository.save(any<Credit>()) } answers { firstArg<Credit>() }
    }

    @Nested
    @DisplayName("when the contributor is already known")
    inner class ExistingContributor {

        @Test
        fun `reuses it instead of creating a duplicate`() {
            val existing = Contributor(
                id = UUID.randomUUID(),
                contributorType = ContributorType.PERSON,
                name = "Christopher Nolan",
                externalSource = ExternalSourceType.TMDB,
                externalSourceId = "525",
            )
            every {
                contributorRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.TMDB, "525")
            } returns existing

            service.credit(mediaItem, ExternalSourceType.TMDB, "525", "Christopher Nolan", RoleType.DIRECTOR)

            verify(exactly = 0) { contributorRepository.save(any<Contributor>()) }
            val credit = slot<Credit>()
            verify(exactly = 1) { creditRepository.save(capture(credit)) }
            assertSame(existing, credit.captured.contributor)
            assertSame(mediaItem, credit.captured.mediaItem)
            assertEquals(RoleType.DIRECTOR, credit.captured.role)
        }
    }

    @Nested
    @DisplayName("when the contributor is new")
    inner class NewContributor {

        @Test
        fun `creates it with the given type, then credits it`() {
            every {
                contributorRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.IGDB, "99")
            } returns null
            val created = slot<Contributor>()
            every { contributorRepository.save(capture(created)) } answers { firstArg<Contributor>() }

            service.credit(
                mediaItem, ExternalSourceType.IGDB, "99", "CD Projekt Red",
                RoleType.DEVELOPER, ContributorType.ORGANIZATION,
            )

            assertEquals("CD Projekt Red", created.captured.name)
            assertEquals(ContributorType.ORGANIZATION, created.captured.contributorType)
            assertEquals(ExternalSourceType.IGDB, created.captured.externalSource)
            assertEquals("99", created.captured.externalSourceId)

            val credit = slot<Credit>()
            verify(exactly = 1) { creditRepository.save(capture(credit)) }
            assertSame(created.captured, credit.captured.contributor)
            assertEquals(RoleType.DEVELOPER, credit.captured.role)
        }

        @Test
        fun `defaults to a PERSON contributor type when none is given`() {
            every {
                contributorRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.HARDCOVER, "7")
            } returns null
            val created = slot<Contributor>()
            every { contributorRepository.save(capture(created)) } answers { firstArg<Contributor>() }

            service.credit(mediaItem, ExternalSourceType.HARDCOVER, "7", "Frank Herbert", RoleType.AUTHOR)

            assertEquals(ContributorType.PERSON, created.captured.contributorType)
        }
    }
}
