package com.umt.core.media

import com.umt.api.generated.model.MediaItemResponse

interface MediaService {

    fun importMovieFromTmdb(tmdbId: Long): MediaItemResponse

    fun importTvShowFromTmdb(tmdbId: Long): MediaItemResponse

    fun syncUpcomingMovies(): List<MediaItemResponse>

    fun syncUpcomingTvSeries(): List<MediaItemResponse>

    fun syncUpcomingAlbums(): List<MediaItemResponse>

    fun syncUpcomingGames(): List<MediaItemResponse>

    fun syncUpcomingBooks(): List<MediaItemResponse>

    fun getUserRecommendations(userId: Long): List<MediaItemResponse>

}