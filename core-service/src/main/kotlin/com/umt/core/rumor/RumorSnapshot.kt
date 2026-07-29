package com.umt.core.rumor

import com.umt.core.media.MediaItem
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = RumorSnapshot.TABLE_NAME)
class RumorSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = MEDIA_ITEM_ID_COLUMN, nullable = false)
    var mediaItem: MediaItem,

    @field:Column(name = DELAY_PROBABILITY_COLUMN, nullable = false, precision = 5, scale = 2)
    var delayProbability: BigDecimal,

    @field:Column(name = AGGREGATE_SENTIMENT_SCORE_COLUMN)
    var aggregateSentimentScore: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = CONFIDENCE_TREND_COLUMN, nullable = false)
    var confidenceTrend: TrendDirection,

    @field:Column(name = TOP_SOURCE_NAME_COLUMN, length = 100)
    var topSourceName: String? = null,

    @field:Column(name = COMPUTED_AT_COLUMN, nullable = false)
    var computedAt: Instant,
) {
    companion object {
        const val TABLE_NAME = "rumor_snapshot"
        const val ID_COLUMN = "id"
        const val MEDIA_ITEM_ID_COLUMN = "media_item_id"
        const val DELAY_PROBABILITY_COLUMN = "delay_probability"
        const val AGGREGATE_SENTIMENT_SCORE_COLUMN = "aggregate_sentiment_score"
        const val CONFIDENCE_TREND_COLUMN = "confidence_trend"
        const val TOP_SOURCE_NAME_COLUMN = "top_source_name"
        const val COMPUTED_AT_COLUMN = "computed_at"
    }
}

