package com.umt.core.media.tvshow

import com.umt.core.media.MediaItem
import com.umt.core.media.MediaCategory
import com.umt.core.media.ReleaseStatus
import com.umt.core.media.Genre
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A separate table from Movie on purpose - a show's lifecycle (seasons, ongoing/ended) doesn't
 * fit the same shape as a single-release film, even though both come from TMDB.
 */
@Entity
@Table(name = TvShow.TABLE_NAME)
class TvShow(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    override var id: UUID? = null,

    @field:Column(name = TITLE_COLUMN, nullable = false, length = 255)
    override var title: String,

    @field:Column(name = DESCRIPTION_COLUMN)
    override var description: String? = null,

    @field:Column(name = COVER_IMAGE_URL_COLUMN)
    override var coverImageUrl: String? = null,

    @field:Column(name = AGE_RATING_COLUMN, length = 10)
    override var ageRating: String? = null,

    @field:Column(name = RELEASE_DATE_COLUMN)
    override var releaseDate: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = RELEASE_DATE_STATUS_COLUMN, nullable = false)
    override var releaseDateStatus: ReleaseStatus = ReleaseStatus.TBA,

    @field:Column(name = POPULARITY_SCORE_COLUMN, nullable = false)
    override var popularityScore: BigDecimal = BigDecimal.ZERO,

    @field:Column(name = AVERAGE_USER_RATING_COLUMN)
    override var averageUserRating: BigDecimal? = null,

    @field:Column(name = RATING_COUNT_COLUMN, nullable = false)
    override var ratingCount: Int = 0,

    @field:Column(name = FRANCHISE_ID_COLUMN)
    override var franchiseId: UUID? = null,

    @field:Column(name = TMDB_ID_COLUMN, nullable = false, unique = true)
    var tmdbId: String,
) : MediaItem {

    override val mediaCategory: MediaCategory get() = MediaCategory.TV_SHOW

    @ManyToMany
    @JoinTable(
        name = "tv_show_genre",
        joinColumns = [JoinColumn(name = "tv_show_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")],
    )
    var genres: MutableSet<Genre> = mutableSetOf()

    @CreationTimestamp
    @field:Column(name = CREATED_AT_COLUMN, nullable = false, updatable = false)
    var createdAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TvShow) return false
        return tmdbId == other.tmdbId
    }

    override fun hashCode(): Int = tmdbId.hashCode()

    companion object {
        const val TABLE_NAME = "tv_show"
        const val ID_COLUMN = "id"
        const val TITLE_COLUMN = "title"
        const val DESCRIPTION_COLUMN = "description"
        const val COVER_IMAGE_URL_COLUMN = "cover_image_url"
        const val AGE_RATING_COLUMN = "age_rating"
        const val RELEASE_DATE_COLUMN = "release_date"
        const val RELEASE_DATE_STATUS_COLUMN = "release_date_status"
        const val POPULARITY_SCORE_COLUMN = "popularity_score"
        const val AVERAGE_USER_RATING_COLUMN = "average_user_rating"
        const val RATING_COUNT_COLUMN = "rating_count"
        const val FRANCHISE_ID_COLUMN = "franchise_id"
        const val TMDB_ID_COLUMN = "tmdb_id"
        const val CREATED_AT_COLUMN = "created_at"
    }
}
