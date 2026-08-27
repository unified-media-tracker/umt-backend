package com.umt.core.rumor

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RumorSnapshotComputedEvent(
    val mediaItemId: UUID,
    val delayProbability: BigDecimal,
    val aggregateSentimentScore: BigDecimal?,
    val topSourceName: String?,
    val confidenceTrend: TrendDirection?,
    val computedAt: Instant,
)