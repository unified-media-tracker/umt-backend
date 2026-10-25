package com.umt.core.media.movie

import com.umt.core.media.MediaCategory
import com.umt.core.media.Genre
import com.umt.core.media.MediaItem
import com.umt.core.media.ReleaseStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = Movie.TABLE_NAME)
@DiscriminatorValue(value = MediaCategory.MOVIE_DISCRIMINATOR)
class Movie(

    title: String,
    description: String? = null,
    coverImageUrl: String? = null,
    ageRating: String? = null,
    releaseDate: LocalDate? = null,
    releaseDateStatus: ReleaseStatus = ReleaseStatus.TBA,
    popularityScore: BigDecimal = BigDecimal.ZERO,
    averageUserRating: BigDecimal? = null,
    ratingCount: Int = 0,
    franchiseId: UUID? = null,

    @field:Column(name = TMDB_ID_COLUMN, nullable = false, unique = true)
    var tmdbId: String,

    @field:Column(name = RUNTIME_MINUTES_COLUMN)
    var runtimeMinutes: Int? = null,

) : MediaItem(
    title = title,
    description = description,
    coverImageUrl = coverImageUrl,
    ageRating = ageRating,
    releaseDate = releaseDate,
    releaseDateStatus = releaseDateStatus,
    popularityScore = popularityScore,
    averageUserRating = averageUserRating,
    ratingCount = ratingCount,
    franchiseId = franchiseId,
) {

    override var mediaCategory: MediaCategory? = MediaCategory.MOVIE

    @ManyToMany
    @JoinTable(
        name = "movie_genre",
        joinColumns = [JoinColumn(name = "movie_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")],
    )
    var genres: MutableSet<Genre> = mutableSetOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Movie) return false
        return tmdbId == other.tmdbId
    }

    override fun hashCode(): Int = tmdbId.hashCode()

    companion object {
        const val TABLE_NAME = "movie"
        const val ID_COLUMN = "id"
        const val TMDB_ID_COLUMN = "tmdb_id"
        const val RUNTIME_MINUTES_COLUMN = "runtime_minutes"
    }
}
