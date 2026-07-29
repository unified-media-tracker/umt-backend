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

        val previous = rumorSnapshotRepository
            .findByMediaItemIdOrderByComputedAtDesc(event.mediaItemId)
            .firstOrNull()

        val trend = when {
            previous == null -> TrendDirection.STABLE
            event.delayProbability > previous.delayProbability -> TrendDirection.UP
            event.delayProbability < previous.delayProbability -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }

        rumorSnapshotRepository.save(
            RumorSnapshot(
                mediaItem = mediaItem,
                delayProbability = event.delayProbability,
                aggregateSentimentScore = event.aggregateSentimentScore,
                confidenceTrend = trend,
                topSourceName = event.topSourceName,
                computedAt = event.computedAt,
            )
        )
    }
}