package com.umt.core.media.tvshow

import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.TvShowResponse
import com.umt.core.media.Genre
import com.umt.core.media.ResponseExtras
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface TvShowMapper {
    @Mapping(target = "externalSource", constant = "TMDB")
    @Mapping(target = "externalSourceId", source = "tvShow.tmdbId")
    @Mapping(target = "contributors", source = "extras.contributors")
    @Mapping(target = "latestDelayProbability", source = "extras.delayProbability")
    @Mapping(target = "latestConfidenceTrend", source = "extras.confidenceTrend")
    @Mapping(target = "previousReleaseDate", ignore = true)
    fun toResponse(tvShow: TvShow, extras: ResponseExtras): TvShowResponse

    fun toGenreResponse(genre: Genre): GenreResponse
}
