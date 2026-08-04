package com.umt.core.media.tmdb

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaItem
import com.umt.core.media.MediaMapper
import com.umt.core.media.MediaRepository
import com.umt.core.media.MovieDetails
import com.umt.core.media.MovieDetailsRepository
import com.umt.core.media.ReleaseDateSyncService
import com.umt.core.media.genre.Genre
import com.umt.core.media.genre.GenreRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Single-item TMDb import for both movies and TV - its own bean specifically, so @Transactional
 * actually applies. Both syncUpcomingMovies/syncUpcomingTvSeries used to call back into
 * importMovieFromTmdb/importTvShowFromTmdb on the same MediaServiceImpl instance; that's
 * self-invocation, which bypasses Spring's proxy entirely, so @Transactional there was silently
 * a no-op whenever called from the sync loop (it only ever worked via the admin controller,
 * which calls through the bean from the outside). Calling this class's methods from a different
 * bean goes through the proxy correctly either way.
 */
@Component
class TmdbCatalogImporter(
    private val mediaItemRepository: MediaRepository,
    private val movieDetailsRepository: MovieDetailsRepository,
    private val genreRepository: GenreRepository,
    private val tmdbClient: TmdbClient,
    private val mediaMapper: MediaMapper,
    private val mediaEventPublisher: MediaEventPublisher,
    private val releaseDateSyncService: ReleaseDateSyncService,
) {

    // Fetching full details even for an already-known movie is deliberate: it's the only way
    // to notice a studio has pushed the release date since yesterday's sync.
    @Transactional
    fun importMovie(tmdbId: Long): MediaItemResponse {
        val existing = findExisting(tmdbId)
        val tmdbMovie = tmdbClient.fetchMovie(tmdbId)

        existing?.let { return updateExisting(it, tmdbMovie.parsedReleaseDate) }

        val saved = createAndSave(tmdbMovie.toMediaItem(), tmdbMovie.genres)
        movieDetailsRepository.save(MovieDetails(mediaItem = saved, runtimeMinutes = tmdbMovie.runtime))

        return mediaMapper.toResponse(saved)
    }

    // No TvDetails table — unlike movies (runtime) and games (platforms), there's nothing
    // TV-specific to store yet at MVP scope; add one later if that changes.
    @Transactional
    fun importTvShow(tmdbId: Long): MediaItemResponse {
        val existing = findExisting(tmdbId)
        val tmdbShow = tmdbClient.fetchTvShow(tmdbId)

        existing?.let { return updateExisting(it, tmdbShow.parsedReleaseDate) }

        val saved = createAndSave(tmdbShow.toMediaItem(), tmdbShow.genres)
        return mediaMapper.toResponse(saved)
    }

    private fun findExisting(tmdbId: Long): MediaItem? =
        mediaItemRepository.findByExternalSourceAndExternalSourceId(ExternalSourceType.TMDB, tmdbId.toString())

    private fun updateExisting(existing: MediaItem, incomingDate: LocalDate?): MediaItemResponse =
        mediaMapper.toResponse(releaseDateSyncService.updateIfChanged(existing, incomingDate, "TMDb"))

    private fun createAndSave(mediaItem: MediaItem, genres: List<TmdbGenre>): MediaItem {
        mediaItem.genres = resolveGenres(genres)
        val saved = mediaItemRepository.save(mediaItem)
        mediaEventPublisher.publishIfUpcoming(saved)
        return saved
    }

    private fun resolveGenres(genres: List<TmdbGenre>): MutableSet<Genre> =
        genres
            .map { genreRepository.findByName(it.name) ?: genreRepository.save(Genre(name = it.name)) }
            .toMutableSet()
}
