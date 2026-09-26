package com.umt.core.media.music.musicbrainz

import com.umt.core.media.ReleaseStatus
import com.umt.core.media.music.Music
import java.time.LocalDate

fun MusicBrainzReleaseGroup.toMusic(discoveredReleaseDate: LocalDate): Music = Music(
    title = title,
    description = null,
    coverImageUrl = "https://coverartarchive.org/release-group/$id/front-250",
    releaseDate = discoveredReleaseDate,
    releaseDateStatus = if (discoveredReleaseDate.isAfter(LocalDate.now())) ReleaseStatus.ANNOUNCED else ReleaseStatus.RELEASED,
    musicbrainzId = id,
)
