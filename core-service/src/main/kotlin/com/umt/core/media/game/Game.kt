package com.umt.core.media.game

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

@Entity
@Table(name = Game.TABLE_NAME)
class Game(
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

    @field:Column(name = IGDB_ID_COLUMN, nullable = false, unique = true)
    var igdbId: String,
) : MediaItem {

    override val mediaCategory: MediaCategory get() = MediaCategory.GAME

    @ManyToMany
    @JoinTable(
        name = "game_genre",
        joinColumns = [JoinColumn(name = "game_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")],
    )
    var genres: MutableSet<Genre> = mutableSetOf()

    @ManyToMany
    @JoinTable(
        name = "game_platform",
        joinColumns = [JoinColumn(name = "game_id")],
        inverseJoinColumns = [JoinColumn(name = "platform_id")],
    )
    var platforms: MutableSet<Platform> = mutableSetOf()

    @CreationTimestamp
    @field:Column(name = CREATED_AT_COLUMN, nullable = false, updatable = false)
    var createdAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Game) return false
        return igdbId == other.igdbId
    }

    override fun hashCode(): Int = igdbId.hashCode()

    companion object {
        const val TABLE_NAME = "game"
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
        const val IGDB_ID_COLUMN = "igdb_id"
        const val CREATED_AT_COLUMN = "created_at"
    }
}
