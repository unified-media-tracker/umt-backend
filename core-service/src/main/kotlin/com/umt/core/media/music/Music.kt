package com.umt.core.media.music

import com.umt.core.media.MediaCategory
import com.umt.core.media.Genre
import com.umt.core.media.MediaItem
import com.umt.core.media.ReleaseStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = Music.TABLE_NAME)
@DiscriminatorValue(value = MediaCategory.MUSIC_DISCRIMINATOR)
class Music(

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

    @field:Column(name = MUSICBRAINZ_ID_COLUMN, nullable = false, unique = true)
    var musicbrainzId: String,

) : MediaItem(
    mediaCategory = MediaCategory.MUSIC,
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

    @ManyToMany
    @JoinTable(
        name = "music_genre",
        joinColumns = [JoinColumn(name = "music_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")],
    )
    var genres: MutableSet<Genre> = mutableSetOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Music) return false
        return musicbrainzId == other.musicbrainzId
    }

    override fun hashCode(): Int = musicbrainzId.hashCode()

    companion object {
        const val TABLE_NAME = "music"
        const val ID_COLUMN = "id"
        const val MUSICBRAINZ_ID_COLUMN = "musicbrainz_id"
    }
}
