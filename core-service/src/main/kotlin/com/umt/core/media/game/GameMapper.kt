package com.umt.core.media.game

import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.Genre
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface GameMapper {

    @Mapping(target = "externalSource", constant = "IGDB")
    @Mapping(target = "externalSourceId", source = "igdbId")
    @Mapping(target = "runtimeMinutes", ignore = true)
    @Mapping(target = "contributors", ignore = true)
    @Mapping(target = "previousReleaseDate", ignore = true)
    @Mapping(target = "latestDelayProbability", ignore = true)
    @Mapping(target = "latestConfidenceTrend", ignore = true)
    fun toResponse(game: Game): MediaItemResponse

    fun toGenreResponse(genre: Genre): GenreResponse
}
