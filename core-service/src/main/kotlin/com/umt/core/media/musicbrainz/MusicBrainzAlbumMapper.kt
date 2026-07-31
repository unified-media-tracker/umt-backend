package com.umt.core.media.musicbrainz

import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaItem
import com.umt.core.media.MediaType
import com.umt.core.media.ReleaseStatus
import java.time.LocalDate

fun MusicBrainzReleaseGroup.toMediaItem(discoveredReleaseDate: LocalDate): MediaItem = MediaItem(
    mediaType = MediaType.MUSIC,
    title = title,
    description = null,
    coverImageUrl = "https://coverartarchive.org/release-group/$id/front-250",
    releaseDate = discoveredReleaseDate,
    releaseDateStatus = if (discoveredReleaseDate.isAfter(LocalDate.now())) ReleaseStatus.ANNOUNCED else ReleaseStatus.RELEASED,
    externalSource = ExternalSourceType.MUSICBRAINZ,
    externalSourceId = id,
)
