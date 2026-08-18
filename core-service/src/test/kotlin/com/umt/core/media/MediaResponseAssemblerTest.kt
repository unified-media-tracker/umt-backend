package com.umt.core.media

import com.umt.api.generated.model.ContributorResponse
import com.umt.api.generated.model.ContributorType as ApiContributorType
import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaType as ApiMediaType
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.contribution.Contributor
import com.umt.core.contribution.ContributorType
import com.umt.core.contribution.Credit
import com.umt.core.contribution.CreditRepository
import com.umt.core.contribution.RoleType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class MediaResponseAssemblerTest {

    private lateinit var creditRepository: CreditRepository
    private lateinit var mediaMapper: MediaMapper
    private lateinit var assembler: MediaResponseAssembler

    private val mediaItemId = UUID.randomUUID()

    private val mediaItem = MediaItem(
        id = mediaItemId,
        mediaType = MediaType.MOVIE,
        title = "Inception",
        externalSource = ExternalSourceType.TMDB,
        externalSourceId = "27205",
    )

    private fun baseResponse() = MediaItemResponse(
        id = mediaItemId,
        mediaType = ApiMediaType.MOVIE,
        title = "Inception",
        releaseDateStatus = ApiReleaseStatus.RELEASED,
        popularityScore = BigDecimal.ZERO,
        ratingCount = 0,
        externalSource = ApiExternalSourceType.TMDB,
        externalSourceId = "27205",
        contributors = emptyList(),
    )

    @BeforeEach
    fun setUp() {
        creditRepository = mockk()
        mediaMapper = mockk()
        assembler = MediaResponseAssembler(creditRepository, mediaMapper)
        every { mediaMapper.toResponse(mediaItem) } returns baseResponse()
    }

    @Test
    fun `fills in contributors from a fresh credit query`() {
        val contributor = Contributor(
            id = UUID.randomUUID(),
            contributorType = ContributorType.PERSON,
            name = "Christopher Nolan",
            externalSource = ExternalSourceType.TMDB,
            externalSourceId = "525",
        )
        val credit = Credit(mediaItem = mediaItem, contributor = contributor, role = RoleType.DIRECTOR)
        val contributorResponse = ContributorResponse(
            id = contributor.id!!,
            name = "Christopher Nolan",
            contributorType = ApiContributorType.PERSON,
            role = com.umt.api.generated.model.RoleType.DIRECTOR,
        )
        every { creditRepository.findByMediaItemId(mediaItemId) } returns listOf(credit)
        every { mediaMapper.toContributorResponse(credit) } returns contributorResponse

        val result = assembler.assemble(mediaItem)

        assertEquals(listOf(contributorResponse), result.contributors)
        assertEquals("Inception", result.title)
    }

    @Test
    fun `an item with no credits gets an empty contributors list`() {
        every { creditRepository.findByMediaItemId(mediaItemId) } returns emptyList()

        val result = assembler.assemble(mediaItem)

        assertTrue(result.contributors.isNullOrEmpty())
    }

    @Test
    fun `assembleList assembles every item independently`() {
        val other = MediaItem(
            id = UUID.randomUUID(),
            mediaType = MediaType.GAME,
            title = "Half-Life 3",
            externalSource = ExternalSourceType.IGDB,
            externalSourceId = "1",
        )
        every { creditRepository.findByMediaItemId(mediaItemId) } returns emptyList()
        every { creditRepository.findByMediaItemId(other.id!!) } returns emptyList()
        every { mediaMapper.toResponse(other) } returns baseResponse().copy(id = other.id!!, title = "Half-Life 3")

        val results = assembler.assembleList(listOf(mediaItem, other))

        assertEquals(listOf("Inception", "Half-Life 3"), results.map { it.title })
    }
}
