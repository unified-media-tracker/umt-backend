package com.umt.core.media.hardcover

import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaItem
import com.umt.core.media.MediaType
import com.umt.core.media.ReleaseStatus
import java.time.LocalDate

val HardcoverBook.parsedReleaseDate: LocalDate?
    get() = releaseDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

// contribution == null marks the primary author;
// falls back to the first credited contributor if a book has none marked that way, rather than crediting nobody.
val HardcoverBook.primaryAuthor: HardcoverAuthor?
    get() = contributions.firstOrNull { it.contribution == null }?.author
        ?: contributions.firstOrNull()?.author

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
