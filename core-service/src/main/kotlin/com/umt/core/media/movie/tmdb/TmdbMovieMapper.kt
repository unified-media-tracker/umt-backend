package com.umt.core.media.movie.tmdb

import com.umt.core.media.ReleaseStatus
import com.umt.core.media.movie.Movie
import java.time.LocalDate

val TmdbMovieResponse.parsedReleaseDate: LocalDate?
    get() = releaseDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

// Genres and runtime aren't set here - genres need a GenreRepository lookup (find-or-create
// against the shared vocabulary), runtime is a plain field the importer sets straight from
// tmdbMovie.runtime once this constructs the rest of the row.
fun TmdbMovieResponse.toMovie(): Movie = Movie(
    title = title,
    description = overview,
    coverImageUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
    releaseDate = parsedReleaseDate,
    releaseDateStatus = mapReleaseStatus(status, releaseDate),
    tmdbId = id.toString(),
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
