package com.umt.core.media.movie

import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.Genre
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface MovieMapper {

    // contributors/latestDelayProbability/latestConfidenceTrend/previousReleaseDate all live
    // outside Movie (credit and rumor_snapshot are loosely referenced, shared tables) -
    // MediaResponseAssembler queries and .copy()s them in afterwards, via the shared
    // ContributorMapper for contributors (same mapping every type needs, kept in one place).
    @Mapping(target = "externalSource", constant = "TMDB")
    @Mapping(target = "externalSourceId", source = "tmdbId")
    @Mapping(target = "contributors", ignore = true)
    @Mapping(target = "previousReleaseDate", ignore = true)
    @Mapping(target = "latestDelayProbability", ignore = true)
    @Mapping(target = "latestConfidenceTrend", ignore = true)
    fun toResponse(movie: Movie): MediaItemResponse

    fun toGenreResponse(genre: Genre): GenreResponse
}
