package com.umt.core.media.tmdb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbTvShowResponse(
    val id: Long,
    val name: String,
    val status: String?,
    val overview: String?,
    @JsonProperty("poster_path") val posterPath: String?,
    @JsonProperty("first_air_date") val firstAirDate: String?,
    val genres: List<TmdbGenre> = emptyList(),
)
