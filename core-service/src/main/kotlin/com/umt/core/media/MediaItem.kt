package com.umt.core.media

import com.umt.core.media.genre.Genre
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class MediaType { MOVIE, GAME, BOOK, MUSIC }
enum class ReleaseStatus { TBA, ANNOUNCED, RUMORED, CONFIRMED, DELAYED, RELEASED, CANCELED }
enum class ExternalSourceType { TMDB, IGDB, GOODREADS, SPOTIFY }

@Entity
@Table(name = MediaItem.TABLE_NAME)
class MediaItem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = MEDIA_TYPE_COLUMN, nullable = false)
    var mediaType: MediaType,

    @field:Column(name = TITLE_COLUMN, nullable = false, length = 255)
    var title: String,

    @field:Column(name = DESCRIPTION_COLUMN)
    var description: String? = null,

    @field:Column(name = COVER_IMAGE_URL_COLUMN)
    var coverImageUrl: String? = null,

    @field:Column(name = AGE_RATING_COLUMN, length = 10)
    var ageRating: String? = null,

    @field:Column(name = RELEASE_DATE_COLUMN)
    var releaseDate: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = RELEASE_DATE_STATUS_COLUMN, nullable = false)
    var releaseDateStatus: ReleaseStatus = ReleaseStatus.TBA,

    @field:Column(name = POPULARITY_SCORE_COLUMN, nullable = false)
    var popularityScore: BigDecimal = BigDecimal.ZERO,

    @field:Column(name = AVERAGE_USER_RATING_COLUMN)
    var averageUserRating: BigDecimal? = null,

    @field:Column(name = RATING_COUNT_COLUMN, nullable = false)
    var ratingCount: Int = 0,

    @field:Column(name = FRANCHISE_ID_COLUMN)
    var franchiseId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = EXTERNAL_SOURCE_COLUMN, nullable = false)
    var externalSource: ExternalSourceType,

    @field:Column(name = EXTERNAL_SOURCE_ID_COLUMN, nullable = false, length = 100)
    var externalSourceId: String,
) {
    @ManyToMany
    @JoinTable(
        name = "media_item_genre",
        joinColumns = [JoinColumn(name = "media_item_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")],
    )
    var genres: MutableSet<Genre> = mutableSetOf()

    @CreationTimestamp
    @field:Column(name = CREATED_AT_COLUMN, nullable = false, updatable = false)
    var createdAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaItem) return false
        return externalSource == other.externalSource && externalSourceId == other.externalSourceId
    }

    override fun hashCode(): Int = externalSource.hashCode() * 31 + externalSourceId.hashCode()

    companion object {
        const val TABLE_NAME = "media_item"
        const val ID_COLUMN = "id"
        const val MEDIA_TYPE_COLUMN = "media_type"
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
        const val EXTERNAL_SOURCE_COLUMN = "external_source"
        const val EXTERNAL_SOURCE_ID_COLUMN = "external_source_id"
        const val CREATED_AT_COLUMN = "created_at"
    }
}