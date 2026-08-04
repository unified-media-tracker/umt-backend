package com.umt.core.media.igdb

import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaItem
import com.umt.core.media.MediaType
import com.umt.core.media.ReleaseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val IgdbGame.parsedReleaseDate: LocalDate?
    get() = firstReleaseDate?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() }

fun IgdbGame.toMediaItem(): MediaItem {
    val releaseDate = parsedReleaseDate

    return MediaItem(
        mediaType = MediaType.GAME,
        title = name,
        description = summary,
        coverImageUrl = cover?.imageId?.let { "https://images.igdb.com/igdb/image/upload/t_cover_big/$it.jpg" },
        releaseDate = releaseDate,
        releaseDateStatus = if (releaseDate != null && releaseDate.isAfter(LocalDate.now())) ReleaseStatus.ANNOUNCED else ReleaseStatus.RELEASED,
        externalSource = ExternalSourceType.IGDB,
        externalSourceId = id.toString(),
    )
}
