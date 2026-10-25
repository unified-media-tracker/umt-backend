package com.umt.core.media

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class MediaCategory {
    MOVIE, TV_SHOW, GAME, BOOK, MUSIC;

    companion object {
        const val MOVIE_DISCRIMINATOR = "MOVIE"
        const val TV_SHOW_DISCRIMINATOR = "TV_SHOW"
        const val GAME_DISCRIMINATOR = "GAME"
        const val BOOK_DISCRIMINATOR = "BOOK"
        const val MUSIC_DISCRIMINATOR = "MUSIC"
    }
}

enum class ReleaseStatus { TBA, ANNOUNCED, RUMORED, CONFIRMED, DELAYED, RELEASED, CANCELED }
enum class ExternalSourceType { TMDB, IGDB, MUSICBRAINZ, HARDCOVER }

@Entity
@Table(name = MediaItem.TABLE_NAME)
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = MediaItem.DISCRIMINATOR_COLUMN)
abstract class MediaItem (

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

) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = DISCRIMINATOR_COLUMN, insertable = false, updatable = false)
    var mediaCategory: MediaCategory? = null
        protected set

    @CreationTimestamp
    @field:Column(name = CREATED_AT_COLUMN, nullable = false, updatable = false)
    var createdAt: Instant? = null

    companion object {
        const val TABLE_NAME = "media_item"
        const val DISCRIMINATOR_COLUMN = "media_category"
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
        const val CREATED_AT_COLUMN = "created_at"
    }

}
