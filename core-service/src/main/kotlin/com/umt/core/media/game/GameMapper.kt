package com.umt.core.media.game

import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.GameResponse
import com.umt.core.media.Genre
import com.umt.core.media.ResponseExtras
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface GameMapper {
    @Mapping(target = "externalSource", constant = "IGDB")
    @Mapping(target = "externalSourceId", source = "game.igdbId")
    @Mapping(target = "contributors", source = "extras.contributors")
    @Mapping(target = "latestDelayProbability", source = "extras.delayProbability")
    @Mapping(target = "latestConfidenceTrend", source = "extras.confidenceTrend")
    @Mapping(target = "previousReleaseDate", ignore = true)
    fun toResponse(game: Game, extras: ResponseExtras): GameResponse

    fun toGenreResponse(genre: Genre): GenreResponse
}
