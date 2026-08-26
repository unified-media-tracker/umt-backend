package com.umt.core.rumor

import com.umt.core.media.MediaRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class RumorSnapshotEventConsumer(
    private val mediaRepository: MediaRepository,
    private val rumorSnapshotRepository: RumorSnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitMQConfig.RUMOR_SNAPSHOT_COMPUTED_QUEUE])
    fun handle(event: RumorSnapshotComputedEvent) {
        val mediaItem = mediaRepository.findById(event.mediaItemId).orElse(null)
        if (mediaItem == null) {
            log.warn("Rumor event for unknown media_item_id={}, skipping", event.mediaItemId)
            return
        }

        rumorSnapshotRepository.save(
            RumorSnapshot(
                mediaItem = mediaItem,
                delayProbability = event.delayProbability,
                aggregateSentimentScore = event.aggregateSentimentScore,
                confidenceTrend = event.confidenceTrend,
                topSourceName = event.topSourceName,
                computedAt = event.computedAt,
            )
        )
    }
}