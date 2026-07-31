package com.umt.core.media

import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.genre.Genre
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface MediaMapper {

    fun toResponse(mediaItem: MediaItem): MediaItemResponse

    fun toGenreResponse(genre: Genre): GenreResponse

    fun toListResponse(mediaItems: List<MediaItem>): List<MediaItemResponse>
}