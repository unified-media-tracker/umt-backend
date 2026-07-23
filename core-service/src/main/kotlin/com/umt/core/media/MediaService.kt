package com.umt.core.media

interface MediaService {

    fun importMovieFromTmdb(tmdbId: Long): MediaItem
}