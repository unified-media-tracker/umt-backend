package com.umt.core.media.book

import com.umt.api.generated.model.GenreResponse
import com.umt.api.generated.model.BookResponse
import com.umt.core.media.Genre
import com.umt.core.media.ResponseExtras
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface BookMapper {
    @Mapping(target = "externalSource", constant = "HARDCOVER")
    @Mapping(target = "externalSourceId", source = "book.hardcoverId")
    @Mapping(target = "contributors", source = "extras.contributors")
    @Mapping(target = "latestDelayProbability", source = "extras.delayProbability")
    @Mapping(target = "latestConfidenceTrend", source = "extras.confidenceTrend")
    @Mapping(target = "previousReleaseDate", ignore = true)
    fun toResponse(book: Book, extras: ResponseExtras): BookResponse

    fun toGenreResponse(genre: Genre): GenreResponse
}
