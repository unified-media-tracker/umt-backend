package com.umt.core.media

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class MediaCategory { MOVIE, TV_SHOW, GAME, BOOK, MUSIC }
enum class ReleaseStatus { TBA, ANNOUNCED, RUMORED, CONFIRMED, DELAYED, RELEASED, CANCELED }
enum class ExternalSourceType { TMDB, IGDB, MUSICBRAINZ, HARDCOVER }

/**
 * Plain Kotlin interface, not a JPA entity or @MappedSuperclass — Movie/TvShow/Game/Book/Music
 * are five independent tables, each with only the fields that genuinely apply to that type. This
 * exists purely, so the handful of things that are legitimately the same across all five (crediting
 * a contributor, recording a delay-probability snapshot, logging a release-status change,
 * publishing the media.imported/media.released events, cross-type search) can be written once
 * against one type instead of five times. Hibernate never sees this interface.
 */
interface MediaItem {
    val id: UUID?
    val mediaCategory: MediaCategory
    val title: String
    val description: String?
    val coverImageUrl: String?
    val ageRating: String?
    var releaseDate: LocalDate?
    var releaseDateStatus: ReleaseStatus
    val popularityScore: BigDecimal
    val averageUserRating: BigDecimal?
    val ratingCount: Int
    val franchiseId: UUID?
}
