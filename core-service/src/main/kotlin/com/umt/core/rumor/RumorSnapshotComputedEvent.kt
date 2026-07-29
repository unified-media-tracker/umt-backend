package com.umt.core.rumor

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class TrendDirection { UP, DOWN, STABLE }

data class RumorSnapshotComputedEvent(
    val mediaItemId: UUID,
    val delayProbability: BigDecimal,
    val aggregateSentimentScore: BigDecimal?,
    val topSourceName: String?,
    val computedAt: Instant,
)