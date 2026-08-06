package com.umt.core.media

import com.umt.core.rumor.RabbitMQConfig
import io.mockk.every
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
 * ai-analyser only ever reacts to 'media.imported', so publishing the wrong things here is
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
    ) = MediaItem(
        id = id,
        mediaType = MediaType.GAME,
        title = title,
        releaseDate = LocalDate.of(2026, 12, 1),
        releaseDateStatus = status,
        popularityScore = BigDecimal.ONE,
        externalSource = ExternalSourceType.IGDB,
        externalSourceId = "1030",
    )

    @Test
    fun `publishes an upcoming item to the events exchange with the media-imported routing key`() {
        val id = UUID.randomUUID()

        publisher.publishIfUpcoming(mediaItem(id = id, title = "Silksong"))

        val payload = slot<MediaImportedEvent>()
        verify(exactly = 1) {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE,
                RabbitMQConfig.MEDIA_IMPORTED_ROUTING_KEY,
                capture(payload),
            )
        }
        assertEquals(id, payload.captured.mediaItemId)
        assertEquals("Silksong", payload.captured.title)
    }

    @Test
    fun `does not publish an already released item`() {
        publisher.publishIfUpcoming(mediaItem(status = ReleaseStatus.RELEASED))

        verify(exactly = 0) {
            rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<Any>())
        }
    }

    @Test
    fun `does not publish an unsaved item that has no id yet`() {
        publisher.publishIfUpcoming(mediaItem(id = null))

        verify(exactly = 0) {
            rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<Any>())
        }
    }

    @Test
    fun `publishes every non-released status`() {
        val publishable = ReleaseStatus.entries.filter { it != ReleaseStatus.RELEASED }

        publishable.forEach { status ->
            publisher.publishIfUpcoming(mediaItem(status = status))
        }

        verify(exactly = publishable.size) {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE,
                RabbitMQConfig.MEDIA_IMPORTED_ROUTING_KEY,
                any<MediaImportedEvent>(),
            )
        }
    }
}
