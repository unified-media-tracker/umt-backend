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
    fun mediaAnalysisRequestedQueue(): Queue = Queue(MEDIA_ANALYSIS_REQUESTED_QUEUE, true)

    @Bean
    fun mediaAnalysisRequestedBinding(mediaAnalysisRequestedQueue: Queue, eventsExchange: TopicExchange): Binding =
        BindingBuilder.bind(mediaAnalysisRequestedQueue).to(eventsExchange).with(MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY)

    @Bean
    fun mediaReleasedQueue(): Queue = Queue(MEDIA_RELEASED_QUEUE, true)

    @Bean
    fun mediaReleasedBinding(mediaReleasedQueue: Queue, eventsExchange: TopicExchange): Binding =
        BindingBuilder.bind(mediaReleasedQueue).to(eventsExchange).with(MEDIA_RELEASED_ROUTING_KEY)

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
        const val MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY = "media.analysis.requested"
        const val MEDIA_ANALYSIS_REQUESTED_QUEUE = "ai-analyser.media-analysis-requested"
        const val MEDIA_RELEASED_ROUTING_KEY = "media.released"
        const val MEDIA_RELEASED_QUEUE = "ai-analyser.media-released"
    }
}
