package com.umt.core.media.dto.response

import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaType
import com.umt.core.media.ReleaseStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class MediaItemResponse(
    val id: UUID,
    val mediaType: MediaType,
    val title: String,
    val description: String?,
    val coverImageUrl: String?,
    val ageRating: String?,
    val releaseDate: LocalDate?,
    val releaseDateStatus: ReleaseStatus,
    val popularityScore: BigDecimal,
    val averageUserRating: BigDecimal?,
    val ratingCount: Int,
    val franchiseId: UUID?,
    val externalSource: ExternalSourceType,
    val externalSourceId: String,
    val genres: Set<GenreResponse> = emptySet()
)

data class GenreResponse(
    val id: UUID,
    val name: String
)
