package com.umt.core.media.tmdb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbMovieResponse(
    val id: Long,
    val title: String,
    val overview: String?,
    @JsonProperty("poster_path") val posterPath: String?,
    @JsonProperty("release_date") val releaseDate: String?,
    val runtime: Int?,
    val genres: List<TmdbGenre>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbGenre(
    val id: Long,
    val name: String,
)