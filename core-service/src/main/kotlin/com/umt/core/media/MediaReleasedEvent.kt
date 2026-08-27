package com.umt.core.media

import java.time.LocalDate
import java.util.UUID

data class MediaReleasedEvent(
    val mediaItemId: UUID,
    val actualReleaseDate: LocalDate,
)
