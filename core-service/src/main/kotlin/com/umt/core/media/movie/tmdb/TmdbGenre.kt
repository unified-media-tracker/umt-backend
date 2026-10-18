package com.umt.core.media.movie.tmdb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// Shared: movies and TV shows both report genres in this exact shape.
@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbGenre(
    val id: Long,
    val name: String,
)
