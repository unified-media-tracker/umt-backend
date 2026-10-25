package com.umt.core.media.tvshow

import com.umt.core.media.MediaCategory
import com.umt.core.media.Genre
import com.umt.core.media.MediaItem
import com.umt.core.media.ReleaseStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * A separate table from Movie on purpose - a show's lifecycle (seasons, ongoing/ended) doesn't
 * fit the same shape as a single-release film, even though both come from TMDB.
 */
@Entity
@Table(name = TvShow.TABLE_NAME)
@DiscriminatorValue(value = MediaCategory.TV_SHOW_DISCRIMINATOR)
class TvShow(

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

    final override var mediaCategory: MediaCategory? = MediaCategory.TV_SHOW

    @ManyToMany
    @JoinTable(
        name = "tv_show_genre",
        joinColumns = [JoinColumn(name = "tv_show_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")],
    )
    var genres: MutableSet<Genre> = mutableSetOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TvShow) return false
        return tmdbId == other.tmdbId
    }

    override fun hashCode(): Int = tmdbId.hashCode()

    companion object {
        const val TABLE_NAME = "tv_show"
        const val ID_COLUMN = "id"
        const val TMDB_ID_COLUMN = "tmdb_id"
    }
}
