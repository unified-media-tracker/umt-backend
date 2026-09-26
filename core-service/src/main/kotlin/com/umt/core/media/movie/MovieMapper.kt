package com.umt.core.media.movie

import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.MovieResponse
import com.umt.core.media.Genre
import com.umt.core.media.ResponseExtras
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface MovieMapper {
    @Mapping(target = "externalSource", constant = "TMDB")
    @Mapping(target = "externalSourceId", source = "movie.tmdbId")
    @Mapping(target = "contributors", source = "extras.contributors")
    @Mapping(target = "latestDelayProbability", source = "extras.delayProbability")
    @Mapping(target = "latestConfidenceTrend", source = "extras.confidenceTrend")
    @Mapping(target = "previousReleaseDate", ignore = true)
    fun toResponse(movie: Movie, extras: ResponseExtras): MovieResponse

    fun toGenreResponse(genre: Genre): GenreResponse
}
