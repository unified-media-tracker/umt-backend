package com.umt.core.media

import java.util.UUID

data class MediaImportedEvent(
    val mediaItemId: UUID,
    val title: String
)
