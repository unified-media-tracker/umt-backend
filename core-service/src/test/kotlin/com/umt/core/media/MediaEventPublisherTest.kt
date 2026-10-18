package com.umt.core.media

import com.umt.core.media.game.Game
import com.umt.core.rumor.RabbitMQConfig
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * ai-analyser only ever reacts to 'media.analysis.requested', so publishing the wrong things here is
 * expensive: an already-released item would trigger a pointless LLM run over news that can no
 * longer change anything.
 */
class MediaEventPublisherTest {

    private lateinit var rabbitTemplate: RabbitTemplate
    private lateinit var publisher: MediaEventPublisher

    @BeforeEach
    fun setUp() {
        rabbitTemplate = mockk(relaxed = true)
        publisher = MediaEventPublisher(rabbitTemplate)
    }

    private fun mediaItem(
        id: UUID? = UUID.randomUUID(),
        status: ReleaseStatus = ReleaseStatus.ANNOUNCED,
        title: String = "Silksong",
        releaseDate: LocalDate? = LocalDate.of(2026, 12, 1),
    ) = Game(
        id = id,
        title = title,
        releaseDate = releaseDate,
        releaseDateStatus = status,
        popularityScore = BigDecimal.ONE,
        igdbId = "1030",
    )

    @Test
    fun `publishes an upcoming item to the events exchange with the media-analysis-requested routing key`() {
        val id = UUID.randomUUID()

        publisher.requestAnalysisIfUpcoming(
            mediaItem(id = id, title = "Silksong", releaseDate = LocalDate.of(2026, 12, 1)),
        )

        val payload = slot<MediaAnalysisRequestedEvent>()
        verify(exactly = 1) {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE,
                RabbitMQConfig.MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY,
                capture(payload),
            )
        }
        assertEquals(id, payload.captured.mediaItemId)
        assertEquals("Silksong", payload.captured.title)
        assertEquals(MediaCategory.GAME, payload.captured.mediaCategory)
        assertEquals(LocalDate.of(2026, 12, 1), payload.captured.releaseDate)
    }

    @Test
    fun `publishes a null release date as-is for a TBA item`() {
        publisher.requestAnalysisIfUpcoming(mediaItem(releaseDate = null))

        val payload = slot<MediaAnalysisRequestedEvent>()
        verify(exactly = 1) {
            rabbitTemplate.convertAndSend(any<String>(), any<String>(), capture(payload))
        }
        assertEquals(null, payload.captured.releaseDate)
    }

    @Test
    fun `does not publish an already released item`() {
        publisher.requestAnalysisIfUpcoming(mediaItem(status = ReleaseStatus.RELEASED))

        verify(exactly = 0) {
            rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<Any>())
        }
    }

    @Test
    fun `does not publish an unsaved item that has no id yet`() {
        publisher.requestAnalysisIfUpcoming(mediaItem(id = null))

        verify(exactly = 0) {
            rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<Any>())
        }
    }

    @Test
    fun `publishes every non-released status`() {
        val publishable = ReleaseStatus.entries.filter { it != ReleaseStatus.RELEASED }

        publishable.forEach { status ->
            publisher.requestAnalysisIfUpcoming(mediaItem(status = status))
        }

        verify(exactly = publishable.size) {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE,
                RabbitMQConfig.MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY,
                any<MediaAnalysisRequestedEvent>(),
            )
        }
    }

    @Test
    fun `publishReleased sends the media-released routing key with the item's id and release date`() {
        val id = UUID.randomUUID()

        publisher.publishReleased(mediaItem(id = id, releaseDate = LocalDate.of(2026, 8, 27)))

        val payload = slot<MediaReleasedEvent>()
        verify(exactly = 1) {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE,
                RabbitMQConfig.MEDIA_RELEASED_ROUTING_KEY,
                capture(payload),
            )
        }
        assertEquals(id, payload.captured.mediaItemId)
        assertEquals(LocalDate.of(2026, 8, 27), payload.captured.actualReleaseDate)
    }

    @Test
    fun `publishReleased does nothing for an item with no release date`() {
        publisher.publishReleased(mediaItem(releaseDate = null))

        verify(exactly = 0) {
            rabbitTemplate.convertAndSend(any<String>(), RabbitMQConfig.MEDIA_RELEASED_ROUTING_KEY, any<Any>())
        }
    }

    @Test
    fun `publishReleased does nothing for an unsaved item that has no id yet`() {
        publisher.publishReleased(mediaItem(id = null))

        verify(exactly = 0) {
            rabbitTemplate.convertAndSend(any<String>(), RabbitMQConfig.MEDIA_RELEASED_ROUTING_KEY, any<Any>())
        }
    }
}
