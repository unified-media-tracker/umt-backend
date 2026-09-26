package com.umt.core.media

import com.umt.api.generated.model.ContributorResponse
import com.umt.api.generated.model.TrendDirection
import java.math.BigDecimal

/** What sits outside the media tables (credits, rumor_snapshot) and goes into every response. */
data class ResponseExtras(
    val contributors: List<ContributorResponse>,
    val delayProbability: BigDecimal?,
    val confidenceTrend: TrendDirection?,
) {
    companion object {
        val NONE = ResponseExtras(emptyList(), null, null)
    }
}
