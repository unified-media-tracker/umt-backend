package com.umt.core.media

import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.core.media.book.BookRepository
import com.umt.core.media.book.hardcover.HardcoverClient
import com.umt.core.media.game.GameRepository
import com.umt.core.media.game.igdb.IgdbClient
import com.umt.core.media.movie.MovieRepository
import com.umt.core.media.movie.tmdb.TmdbClient
import com.umt.core.media.music.MusicRepository
import com.umt.core.media.tvshow.TvShowRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * "More like this" for one specific title.
 * Has a different upstream source per type: TMDb's own /recommendations for movie and TV,
 * IGDB's similar_games field for games, Hardcover's cached_similar_book_ids for books.
 */
@Service
class SimilarMediaService(
    private val movieRepository: MovieRepository,
    private val tvShowRepository: TvShowRepository,
    private val gameRepository: GameRepository,
    private val bookRepository: BookRepository,
    private val musicRepository: MusicRepository,
    private val tmdbClient: TmdbClient,
    private val igdbClient: IgdbClient,
    private val hardcoverClient: HardcoverClient,
    private val mediaResponseAssembler: MediaResponseAssembler,
    private val mediaMapper: MediaMapper,
) {
    // mediaCategory given: one table, one lookup, straight into that type's own similarity source.
    // mediaCategory omitted: try-each-table order.
    fun getSimilarMedia(id: UUID, mediaCategory: ApiMediaCategory? = null): List<MediaItemResponse> {
        if (mediaCategory != null) return getSimilarMediaByKnownType(id, mediaMapper.toDomainMediaCategory(mediaCategory))

        movieRepository.findById(id).orElse(null)?.let { return similarToMovie(it.tmdbId) }
        tvShowRepository.findById(id).orElse(null)?.let { return similarToTvShow(it.tmdbId) }
        gameRepository.findById(id).orElse(null)?.let { return similarToGame(it.igdbId) }
        bookRepository.findById(id).orElse(null)?.let { return similarToBook(it.hardcoverId) }
        musicRepository.findById(id).orElse(null)?.let { return emptyList() }
        throw NoSuchElementException("Media item $id not found")
    }

    private fun getSimilarMediaByKnownType(id: UUID, mediaCategory: MediaCategory): List<MediaItemResponse> {
        // Movie/TvShow/Game/Book: null here means the id genuinely doesn't exist under that
        // type - a 404, distinct from "found it, but zero similar titles" (a valid empty list).
        // Music: no repository lookup needed since it's always an empty list either way, but
        // still 404s for an id that isn't actually music - orElse in that branch is that check.
        return when (mediaCategory) {
            MediaCategory.MOVIE -> movieRepository.findById(id).orElse(null)?.let { similarToMovie(it.tmdbId) }
            MediaCategory.TV_SHOW -> tvShowRepository.findById(id).orElse(null)?.let { similarToTvShow(it.tmdbId) }
            MediaCategory.GAME -> gameRepository.findById(id).orElse(null)?.let { similarToGame(it.igdbId) }
            MediaCategory.BOOK -> bookRepository.findById(id).orElse(null)?.let { similarToBook(it.hardcoverId) }
            MediaCategory.MUSIC -> musicRepository.findById(id).orElse(null)?.let { emptyList() }
        } ?: throw NoSuchElementException("Media item $id not found")
    }

    private fun similarToMovie(tmdbId: String): List<MediaItemResponse> {
        val candidateIds = tmdbClient.fetchMovieRecommendationIds(tmdbId.toLong()).map { it.toString() }
        val byTmdbId = movieRepository.findByTmdbIdIn(candidateIds).associateBy { it.tmdbId }
        val matches = candidateIds.mapNotNull { byTmdbId[it] }.take(SIMILAR_MEDIA_LIMIT)
        return mediaResponseAssembler.assembleMovieList(matches)
    }

    private fun similarToTvShow(tmdbId: String): List<MediaItemResponse> {
        val candidateIds = tmdbClient.fetchTvRecommendationIds(tmdbId.toLong()).map { it.toString() }
        val byTmdbId = tvShowRepository.findByTmdbIdIn(candidateIds).associateBy { it.tmdbId }
        val matches = candidateIds.mapNotNull { byTmdbId[it] }.take(SIMILAR_MEDIA_LIMIT)
        return mediaResponseAssembler.assembleTvShowList(matches)
    }

    private fun similarToGame(igdbId: String): List<MediaItemResponse> {
        val candidateIds = igdbClient.fetchSimilarGameIds(igdbId.toLong()).map { it.toString() }
        val byIgdbId = gameRepository.findByIgdbIdIn(candidateIds).associateBy { it.igdbId }
        val matches = candidateIds.mapNotNull { byIgdbId[it] }.take(SIMILAR_MEDIA_LIMIT)
        return mediaResponseAssembler.assembleGameList(matches)
    }

    private fun similarToBook(hardcoverId: String): List<MediaItemResponse> {
        val candidateIds = hardcoverClient.fetchSimilarBookIds(hardcoverId.toLong()).map { it.toString() }
        val byHardcoverId = bookRepository.findByHardcoverIdIn(candidateIds).associateBy { it.hardcoverId }
        val matches = candidateIds.mapNotNull { byHardcoverId[it] }.take(SIMILAR_MEDIA_LIMIT)
        return mediaResponseAssembler.assembleBookList(matches)
    }

    companion object {
        const val SIMILAR_MEDIA_LIMIT = 10
    }
}
