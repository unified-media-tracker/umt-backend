package com.umt.core.media

import com.umt.core.rumor.RabbitMQConfig
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

@Component
class MediaEventPublisher(private val rabbitTemplate: RabbitTemplate) {

    fun publishIfUpcoming(mediaItem: MediaItem) {
        if (mediaItem.releaseDateStatus == ReleaseStatus.RELEASED) return
        val id = mediaItem.id ?: return

        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EVENTS_EXCHANGE,
            RabbitMQConfig.MEDIA_IMPORTED_ROUTING_KEY,
            MediaImportedEvent(
                mediaItemId = id, title = mediaItem.title, mediaType = mediaItem.mediaType,
                releaseDate = mediaItem.releaseDate,
            ),
        )
    }
}
