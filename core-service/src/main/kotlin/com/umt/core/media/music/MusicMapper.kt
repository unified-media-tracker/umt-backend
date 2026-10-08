package com.umt.core.media.music

import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.Genre
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface MusicMapper {

    @Mapping(target = "externalSource", constant = "MUSICBRAINZ")
    @Mapping(target = "externalSourceId", source = "musicbrainzId")
    @Mapping(target = "runtimeMinutes", ignore = true)
    @Mapping(target = "contributors", ignore = true)
    @Mapping(target = "previousReleaseDate", ignore = true)
    @Mapping(target = "latestDelayProbability", ignore = true)
    @Mapping(target = "latestConfidenceTrend", ignore = true)
    fun toResponse(music: Music): MediaItemResponse

    fun toGenreResponse(genre: Genre): GenreResponse
}
