package com.umt.core.media.book.hardcover

import com.umt.core.media.ReleaseStatus
import com.umt.core.media.book.Book
import java.time.LocalDate

val HardcoverBook.parsedReleaseDate: LocalDate?
    get() = releaseDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

// contribution == null marks the primary author;
// falls back to the first credited contributor if a book has none marked that way, rather than crediting nobody.
val HardcoverBook.primaryAuthor: HardcoverAuthor?
    get() = contributions.firstOrNull { it.contribution == null }?.author
        ?: contributions.firstOrNull()?.author

fun HardcoverBook.toBook(): Book {
    val date = parsedReleaseDate

    return Book(
        title = title,
        description = description,
        coverImageUrl = image?.url,
        releaseDate = date,
        releaseDateStatus = if (date != null && date.isAfter(LocalDate.now())) ReleaseStatus.ANNOUNCED else ReleaseStatus.RELEASED,
        hardcoverId = id.toString(),
    )
}
