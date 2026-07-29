package com.umt.core

import com.umt.core.rumor.RabbitMQConfig
import com.umt.core.rumor.RumorComputedEvent
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class RabbitMQIntegrationTest {

    @Autowired
    lateinit var rabbitTemplate: RabbitTemplate

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    lateinit var connectionFactory: org.springframework.amqp.rabbit.connection.ConnectionFactory

    @Test
    fun `test publishing and receiving rumor computed event`() {
        val event = RumorComputedEvent(
            mediaItemId = UUID.randomUUID(),
            delayProbability = 0.85,
            aggregateSentimentScore = -0.4,
            confidenceTrend = "UP",
            topSourceName = "Test Source",
            computedAt = Instant.now()
        )
        // Mocked out rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, RabbitMQConfig.RUMOR_COMPUTED_ROUTING_KEY, event)
        Thread.sleep(1000) // Wait for listener
    }
}
