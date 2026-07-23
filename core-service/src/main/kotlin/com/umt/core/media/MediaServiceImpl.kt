package com.umt.core.media

import com.umt.core.media.genre.Genre
import com.umt.core.media.genre.GenreRepository
import com.umt.core.media.tmdb.TmdbClient
import com.umt.core.media.tmdb.toMediaItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MediaServiceImpl(
    private val mediaItemRepository: MediaItemRepository,
    private val movieDetailsRepository: MovieDetailsRepository,
    private val genreRepository: GenreRepository,
    private val tmdbClient: TmdbClient,
) : MediaService {

    @Transactional
    override fun importMovieFromTmdb(tmdbId: Long): MediaItem {
        val existing = mediaItemRepository.findByExternalSourceAndExternalSourceId(
            ExternalSourceType.TMDB, tmdbId.toString(),
        )
        if (existing != null) return existing

        val tmdbMovie = tmdbClient.fetchMovie(tmdbId)
        val mediaItem = tmdbMovie.toMediaItem()

        mediaItem.genres = tmdbMovie.genres
            .map { genreRepository.findByName(it.name) ?: genreRepository.save(Genre(name = it.name)) }
            .toMutableSet()

        val saved = mediaItemRepository.save(mediaItem)

        movieDetailsRepository.save(
            MovieDetails(
                mediaItem = saved,
                runtimeMinutes = tmdbMovie.runtime,
            )
        )

        return saved
    }
}