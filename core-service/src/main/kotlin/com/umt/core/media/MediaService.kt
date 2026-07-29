package com.umt.core.media

import com.umt.core.media.dto.response.MediaItemResponse

interface MediaService {

    fun importMovieFromTmdb(tmdbId: Long): MediaItemResponse

    fun getUserRecommendations(userId: Long): List<MediaItemResponse>

}