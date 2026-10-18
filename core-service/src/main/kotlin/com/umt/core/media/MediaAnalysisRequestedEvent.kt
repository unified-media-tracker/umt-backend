package com.umt.core.media

import java.time.LocalDate
import java.util.UUID

/**
 * Asks the analyser to (re)score one item. Sent when an item is first imported and again on a
 * schedule (MediaAnalysisScheduler), so it's a request, not a record of an import.
 */
data class MediaAnalysisRequestedEvent(
    val mediaItemId: UUID,
    val title: String,
    val mediaCategory: MediaCategory,
    val releaseDate: LocalDate?,
)
