package com.umt.core.rumor

import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class RumorSnapshotEventConsumer(
    private val rumorSnapshotRepository: RumorSnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitMQConfig.RUMOR_SNAPSHOT_COMPUTED_QUEUE])
    fun handle(event: RumorSnapshotComputedEvent) {
        rumorSnapshotRepository.save(
            RumorSnapshot(
                mediaItemId = event.mediaItemId,
                mediaCategory = event.mediaCategory,
                delayProbability = event.delayProbability,
                aggregateSentimentScore = event.aggregateSentimentScore,
                confidenceTrend = event.confidenceTrend,
                topSourceName = event.topSourceName,
                computedAt = event.computedAt,
            )
        )
        log.info("Recorded rumor snapshot for {} {} ({} percent)", event.mediaCategory, event.mediaItemId, event.delayProbability)
    }
}
