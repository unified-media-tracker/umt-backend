package com.umt.core.media

import java.time.LocalDate
import java.util.UUID

data class MediaImportedEvent(
    val mediaItemId: UUID,
    val title: String,
    val mediaType: MediaType,
    val releaseDate: LocalDate?,
)
