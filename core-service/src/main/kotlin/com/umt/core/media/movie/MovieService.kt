package com.umt.core.media.movie

import arrow.core.Either
import com.umt.api.generated.model.MediaItemResponse

interface MovieService {

    // todo: add left type (implement error handling)
    fun getDetailsById(id: String): Either<Any, MediaItemResponse>

    fun importMovieFromTmdb(tmdbId: Long): MediaItemResponse

    fun syncUpcomingMovies(): List<MediaItemResponse>

}