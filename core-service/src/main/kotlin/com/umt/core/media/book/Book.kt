package com.umt.core.media.book

import com.umt.core.media.MediaCategory
import com.umt.core.media.Genre
import com.umt.core.media.MediaItem
import com.umt.core.media.ReleaseStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = Book.TABLE_NAME)
@DiscriminatorValue(value = MediaCategory.BOOK_DISCRIMINATOR)
class Book(

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

    @field:Column(name = HARDCOVER_ID_COLUMN, nullable = false, unique = true)
    var hardcoverId: String,

) : MediaItem(
    mediaCategory = MediaCategory.BOOK,
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
        name = "book_genre",
        joinColumns = [JoinColumn(name = "book_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")],
    )
    var genres: MutableSet<Genre> = mutableSetOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Book) return false
        return hardcoverId == other.hardcoverId
    }

    override fun hashCode(): Int = hardcoverId.hashCode()

    companion object {
        const val TABLE_NAME = "book"
        const val ID_COLUMN = "id"
        const val HARDCOVER_ID_COLUMN = "hardcover_id"
    }
}
