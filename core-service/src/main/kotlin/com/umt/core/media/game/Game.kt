package com.umt.core.media.game

import com.umt.core.media.MediaCategory
import com.umt.core.media.Genre
import com.umt.core.media.MediaItem
import com.umt.core.media.ReleaseStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = Game.TABLE_NAME)
@DiscriminatorValue(value = MediaCategory.GAME_DISCRIMINATOR)
class Game(

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

    @field:Column(name = IGDB_ID_COLUMN, nullable = false, unique = true)
    var igdbId: String,

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

    override var mediaCategory: MediaCategory? = MediaCategory.GAME

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Game) return false
        return igdbId == other.igdbId
    }

    override fun hashCode(): Int = igdbId.hashCode()

    companion object {
        const val TABLE_NAME = "game"
        const val ID_COLUMN = "id"
        const val IGDB_ID_COLUMN = "igdb_id"
    }
}
