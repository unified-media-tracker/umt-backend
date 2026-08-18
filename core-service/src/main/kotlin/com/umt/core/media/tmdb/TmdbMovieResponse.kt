package com.umt.core.media.tmdb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbMovieResponse(
    val id: Long,
    val title: String,
    val status: String?,
    val overview: String?,
    @JsonProperty("poster_path") val posterPath: String?,
    @JsonProperty("release_date") val releaseDate: String?,
    val runtime: Int?,
    val genres: List<TmdbGenre>,
    // Only present when the request used append_to_response=credits (see TmdbClient.fetchMovie).
    val credits: TmdbCredits? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbGenre(
    val id: Long,
    val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCredits(
    val crew: List<TmdbCrewMember> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCrewMember(
    val id: Long,
    val name: String,
    val job: String,
    val department: String,
)