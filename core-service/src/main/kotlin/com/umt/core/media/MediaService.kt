package com.umt.core.media

import com.umt.api.generated.model.MediaItemResponse

interface MediaService {

    fun importMovieFromTmdb(tmdbId: Long): MediaItemResponse

    fun syncUpcomingAlbums(): List<MediaItemResponse>

    fun getUserRecommendations(userId: Long): List<MediaItemResponse>

}