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
    // Unlike movie credits/crew, this is on the base TV response directly - no
    // append_to_response needed to get the show's creators.
    @JsonProperty("created_by") val createdBy: List<TmdbCreator> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCreator(
    val id: Long,
    val name: String,
)
