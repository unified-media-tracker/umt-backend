package com.umt.core.media.music

import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.MusicResponse
import com.umt.core.media.Genre
import com.umt.core.media.ResponseExtras
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface MusicMapper {
    @Mapping(target = "externalSource", constant = "MUSICBRAINZ")
    @Mapping(target = "externalSourceId", source = "music.musicbrainzId")
    @Mapping(target = "contributors", source = "extras.contributors")
    @Mapping(target = "latestDelayProbability", source = "extras.delayProbability")
    @Mapping(target = "latestConfidenceTrend", source = "extras.confidenceTrend")
    @Mapping(target = "previousReleaseDate", ignore = true)
    fun toResponse(music: Music, extras: ResponseExtras): MusicResponse

    fun toGenreResponse(genre: Genre): GenreResponse
}
