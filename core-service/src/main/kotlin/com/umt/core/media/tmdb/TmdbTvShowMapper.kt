package com.umt.core.media.tmdb

import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaItem
import com.umt.core.media.MediaType
import com.umt.core.media.ReleaseStatus
import java.time.LocalDate

val TmdbTvShowResponse.parsedReleaseDate: LocalDate?
    get() = firstAirDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun TmdbTvShowResponse.toMediaItem(): MediaItem = MediaItem(
    mediaType = MediaType.TV_SHOW,
    title = name,
    description = overview,
    coverImageUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
    releaseDate = parsedReleaseDate,
    releaseDateStatus = mapTvReleaseStatus(status, firstAirDate),
    externalSource = ExternalSourceType.TMDB,
    externalSourceId = id.toString(),
)

// TV uses its own status vocabulary — no "Rumored"/"Post Production" here, but it does
// have "Returning Series" for an ongoing show, which movies have no equivalent of.
private fun mapTvReleaseStatus(tmdbStatus: String?, firstAirDate: String?): ReleaseStatus {
    val parsedDate = firstAirDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    return when (tmdbStatus?.trim()?.lowercase()) {
        "planned", "pilot" -> ReleaseStatus.ANNOUNCED

        "in production" -> {
            if (parsedDate != null && parsedDate.isAfter(LocalDate.now())) ReleaseStatus.CONFIRMED
            else ReleaseStatus.ANNOUNCED
        }

        "returning series", "ended" -> {
            if (parsedDate != null && parsedDate.isAfter(LocalDate.now())) ReleaseStatus.CONFIRMED
            else ReleaseStatus.RELEASED
        }

        "canceled", "cancelled" -> ReleaseStatus.CANCELED
        else -> if (parsedDate == null) ReleaseStatus.TBA else ReleaseStatus.ANNOUNCED
    }
}
