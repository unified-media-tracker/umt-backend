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
    releaseDateStatus = if (releaseDate.isNullOrBlank()) ReleaseStatus.TBA else ReleaseStatus.RELEASED,
    externalSource = ExternalSourceType.TMDB,
    externalSourceId = id.toString(),
)