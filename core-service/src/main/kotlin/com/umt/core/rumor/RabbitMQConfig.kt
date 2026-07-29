package com.umt.core.rumor

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.amqp.core.*
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMQConfig {

    @Bean
    fun eventsExchange(): TopicExchange = TopicExchange(EVENTS_EXCHANGE, true, false)

    @Bean
    fun rumorSnapshotComputedQueue(): Queue = Queue(RUMOR_SNAPSHOT_COMPUTED_QUEUE, true)

    @Bean
    fun rumorSnapshotComputedBinding(rumorSnapshotComputedQueue: Queue, eventsExchange: TopicExchange): Binding =
        BindingBuilder.bind(rumorSnapshotComputedQueue).to(eventsExchange).with(RUMOR_SNAPSHOT_COMPUTED_ROUTING_KEY)

    @Bean
    fun mediaImportedQueue(): Queue = Queue(MEDIA_IMPORTED_QUEUE, true)

    @Bean
    fun mediaImportedBinding(mediaImportedQueue: Queue, eventsExchange: TopicExchange): Binding =
        BindingBuilder.bind(mediaImportedQueue).to(eventsExchange).with(MEDIA_IMPORTED_ROUTING_KEY)

    @Bean
    fun jsonMessageConverter(): Jackson2JsonMessageConverter {
        val mapper = jacksonObjectMapper().apply {
            propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        return Jackson2JsonMessageConverter(mapper)
    }

    companion object {
        const val EVENTS_EXCHANGE = "umt.events"
        const val RUMOR_SNAPSHOT_COMPUTED_ROUTING_KEY = "rumor.snapshot.computed"
        const val RUMOR_SNAPSHOT_COMPUTED_QUEUE = "core-service.rumor-snapshot-computed"
        const val MEDIA_IMPORTED_ROUTING_KEY = "media.imported"
        const val MEDIA_IMPORTED_QUEUE = "ai-analyser.media-imported"
    }
}
