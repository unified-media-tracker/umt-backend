package com.umt.core.media.game.igdb

import com.umt.core.media.ReleaseStatus
import com.umt.core.media.game.Game
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val IgdbGame.parsedReleaseDate: LocalDate?
    get() = firstReleaseDate?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() }

fun IgdbGame.toGame(): Game {
    val releaseDate = parsedReleaseDate

    return Game(
        title = name,
        description = summary,
        coverImageUrl = cover?.imageId?.let { "https://images.igdb.com/igdb/image/upload/t_cover_big/$it.jpg" },
        releaseDate = releaseDate,
        releaseDateStatus = if (releaseDate != null && releaseDate.isAfter(LocalDate.now())) ReleaseStatus.ANNOUNCED else ReleaseStatus.RELEASED,
        igdbId = id.toString(),
    )
}
