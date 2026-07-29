package com.umt.core.rumor

import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class RumorComputedListener {

    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitMQConfig.RUMOR_COMPUTED_QUEUE])
    fun onRumorComputed(event: RumorComputedEvent) {
        log.info("Received rumor computed event: {}", event)
    }
}
