package com.umt.core.media.hardcover

import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaItem
import com.umt.core.media.MediaType
import com.umt.core.media.ReleaseStatus
import java.time.LocalDate

val HardcoverBook.parsedReleaseDate: LocalDate?
    get() = releaseDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun HardcoverBook.toMediaItem(): MediaItem {
    val date = parsedReleaseDate

    return MediaItem(
        mediaType = MediaType.BOOK,
        title = title,
        description = description,
        coverImageUrl = image?.url,
        releaseDate = date,
        releaseDateStatus = if (date != null && date.isAfter(LocalDate.now())) ReleaseStatus.ANNOUNCED else ReleaseStatus.RELEASED,
        externalSource = ExternalSourceType.HARDCOVER,
        externalSourceId = id.toString(),
    )
}
