package com.umt.core.rumor

import com.umt.core.media.MediaCategory
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RumorSnapshotComputedEvent(
    val mediaItemId: UUID,
    val mediaCategory: MediaCategory,
    val delayProbability: BigDecimal,
    val aggregateSentimentScore: BigDecimal?,
    val topSourceName: String?,
    val confidenceTrend: TrendDirection?,
    val computedAt: Instant,
)
