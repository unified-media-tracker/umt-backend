package com.umt.core.media.dto

import com.umt.core.media.MediaItem
import com.umt.core.media.dto.response.GenreResponse
import com.umt.core.media.dto.response.MediaItemResponse
import com.umt.core.media.genre.Genre
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface MediaMapper {

    fun toResponse(mediaItem: MediaItem): MediaItemResponse

    fun toGenreResponse(genre: Genre): GenreResponse

    fun toListResponse(mediaItems: List<MediaItem>): List<MediaItemResponse>
}