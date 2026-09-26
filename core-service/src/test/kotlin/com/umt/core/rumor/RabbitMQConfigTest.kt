package com.umt.core.rumor

import com.fasterxml.jackson.databind.ObjectMapper
import com.umt.core.media.MediaAnalysisRequestedEvent
import com.umt.core.media.MediaCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.MessageProperties
import java.time.LocalDate
import java.util.UUID

/**
 * The other end of these messages is Python, so a mismatch fails silently: a renamed queue or a
 * camelCase field just means every request is dropped or rejected. ai-analyser pins the same
 * names in tests/test_consumer.py.
 */
class RabbitMQConfigTest {

    @Test
    fun `the analysis-request names are the ones the analyser declares`() {
        assertEquals("umt.events", RabbitMQConfig.EVENTS_EXCHANGE)
        assertEquals("media.analysis.requested", RabbitMQConfig.MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY)
        assertEquals("ai-analyser.media-analysis-requested", RabbitMQConfig.MEDIA_ANALYSIS_REQUESTED_QUEUE)
    }

    @Test
    fun `the request is serialised with the snake_case fields the analyser reads`() {
        val id = UUID.randomUUID()
        val message = RabbitMQConfig().jsonMessageConverter().toMessage(
            MediaAnalysisRequestedEvent(id, "Digger", MediaCategory.MOVIE, LocalDate.of(2026, 9, 30)),
            MessageProperties(),
        )

        val json = ObjectMapper().readTree(message.body)

        assertEquals(setOf("media_item_id", "title", "media_category", "release_date"), json.fieldNames().asSequence().toSet())
        assertEquals(id.toString(), json["media_item_id"].asText())
        assertEquals("MOVIE", json["media_category"].asText())
        assertEquals("2026-09-30", json["release_date"].asText())
    }
}
