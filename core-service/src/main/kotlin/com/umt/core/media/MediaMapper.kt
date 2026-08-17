package com.umt.core.media

import com.umt.api.generated.model.ContributorResponse
import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.contribution.Credit
import com.umt.core.media.genre.Genre
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface MediaMapper {

    // Doesn't map "contributors" - MediaItem has no field to map it from (see
    // MediaResponseAssembler for why, and where that field actually gets filled in).
    @Mapping(target = "contributors", ignore = true)
    fun toResponse(mediaItem: MediaItem): MediaItemResponse

    fun toGenreResponse(genre: Genre): GenreResponse

    fun toListResponse(mediaItems: List<MediaItem>): List<MediaItemResponse>

    @Mapping(target = "id", source = "contributor.id")
    @Mapping(target = "name", source = "contributor.name")
    @Mapping(target = "contributorType", source = "contributor.contributorType")
    fun toContributorResponse(credit: Credit): ContributorResponse
}