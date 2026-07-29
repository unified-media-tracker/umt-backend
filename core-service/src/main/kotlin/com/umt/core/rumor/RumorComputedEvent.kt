package com.umt.core.rumor

import java.time.Instant
import java.util.UUID

data class RumorComputedEvent(
    val mediaItemId: UUID,
    val delayProbability: Double,
    val aggregateSentimentScore: Double?,
    val confidenceTrend: String,
    val topSourceName: String?,
    val computedAt: Instant
)
