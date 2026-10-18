package com.umt.core.media.tvshow

import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.Genre
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface TvShowMapper {

    @Mapping(target = "externalSource", constant = "TMDB")
    @Mapping(target = "externalSourceId", source = "tmdbId")
    @Mapping(target = "runtimeMinutes", ignore = true)
    @Mapping(target = "contributors", ignore = true)
    @Mapping(target = "previousReleaseDate", ignore = true)
    @Mapping(target = "latestDelayProbability", ignore = true)
    @Mapping(target = "latestConfidenceTrend", ignore = true)
    fun toResponse(tvShow: TvShow): MediaItemResponse

    fun toGenreResponse(genre: Genre): GenreResponse
}
