package com.umt.core.media.tmdb

import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaItem
import com.umt.core.media.MediaType
import com.umt.core.media.ReleaseStatus
import java.time.LocalDate

fun TmdbMovieResponse.toMediaItem(): MediaItem = MediaItem(
    mediaType = MediaType.MOVIE,
    title = title,
    description = overview,
    coverImageUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
    releaseDate = releaseDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
    releaseDateStatus = mapReleaseStatus(status, releaseDate),
    externalSource = ExternalSourceType.TMDB,
    externalSourceId = id.toString(),
)

private fun mapReleaseStatus(tmdbStatus: String?, releaseDate: String?): ReleaseStatus {
    val parsedDate = releaseDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    return when (tmdbStatus?.trim()?.lowercase()) {
        "rumored" -> ReleaseStatus.RUMORED

        "planned" -> ReleaseStatus.ANNOUNCED

        "in production", "post production" -> {
            if (parsedDate != null && parsedDate.isAfter(LocalDate.now())) ReleaseStatus.CONFIRMED
            else ReleaseStatus.ANNOUNCED
        }

        "released" -> {
            if (parsedDate != null && parsedDate.isAfter(LocalDate.now())) ReleaseStatus.CONFIRMED
            else ReleaseStatus.RELEASED
        }

        "canceled", "cancelled" -> ReleaseStatus.CANCELED
        else -> if (parsedDate == null) ReleaseStatus.TBA else ReleaseStatus.ANNOUNCED
    }
}