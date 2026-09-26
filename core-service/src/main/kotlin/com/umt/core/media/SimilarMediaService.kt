package com.umt.core.media

import com.umt.api.generated.model.MediaResponse
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.core.contribution.CreditRepository
import com.umt.core.contribution.RoleType
import com.umt.core.media.book.BookRepository
import com.umt.core.media.book.hardcover.HardcoverClient
import com.umt.core.media.game.GameRepository
import com.umt.core.media.game.igdb.IgdbClient
import com.umt.core.media.movie.MovieRepository
import com.umt.core.media.movie.tmdb.TmdbClient
import com.umt.core.media.music.Music
import com.umt.core.media.music.MusicRepository
import com.umt.core.media.music.similarity.ArtistSimilarityService
import com.umt.core.media.tvshow.TvShowRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * "More like this" for one specific title.
 * Has a different upstream source per type: TMDb's own /recommendations for movie and TV,
 * IGDB's similar_games field for games, Hardcover's cached_similar_book_ids for books,
 * ListenBrainz's similar artists for music.
 */
@Service
class SimilarMediaService(
    private val movieRepository: MovieRepository,
    private val tvShowRepository: TvShowRepository,
    private val gameRepository: GameRepository,
    private val bookRepository: BookRepository,
    private val musicRepository: MusicRepository,
    private val creditRepository: CreditRepository,
    private val tmdbClient: TmdbClient,
    private val igdbClient: IgdbClient,
    private val hardcoverClient: HardcoverClient,
    private val artistSimilarityService: ArtistSimilarityService,
    private val mediaResponseAssembler: MediaResponseAssembler,
    private val mediaMapper: MediaMapper,
) {
    // mediaCategory given: one table, one lookup, straight into that type's own similarity source.
    // mediaCategory omitted: try-each-table order.
    fun getSimilarMedia(id: UUID, mediaCategory: ApiMediaCategory? = null): List<MediaResponse> {
        if (mediaCategory != null) return getSimilarMediaByKnownType(id, mediaMapper.toDomainMediaCategory(mediaCategory))

        movieRepository.findById(id).orElse(null)?.let { return similarToMovie(it.tmdbId) }
        tvShowRepository.findById(id).orElse(null)?.let { return similarToTvShow(it.tmdbId) }
        gameRepository.findById(id).orElse(null)?.let { return similarToGame(it.igdbId) }
        bookRepository.findById(id).orElse(null)?.let { return similarToBook(it.hardcoverId) }
        musicRepository.findById(id).orElse(null)?.let { return similarToMusic(it) }
        throw NoSuchElementException("Media item $id not found")
    }

    private fun getSimilarMediaByKnownType(id: UUID, mediaCategory: MediaCategory): List<MediaResponse> {
        // null here means the id genuinely doesn't exist under that type - a 404, distinct
        // from "found it, but zero similar titles" (a valid empty list).
        return when (mediaCategory) {
            MediaCategory.MOVIE -> movieRepository.findById(id).orElse(null)?.let { similarToMovie(it.tmdbId) }
            MediaCategory.TV_SHOW -> tvShowRepository.findById(id).orElse(null)?.let { similarToTvShow(it.tmdbId) }
            MediaCategory.GAME -> gameRepository.findById(id).orElse(null)?.let { similarToGame(it.igdbId) }
            MediaCategory.BOOK -> bookRepository.findById(id).orElse(null)?.let { similarToBook(it.hardcoverId) }
            MediaCategory.MUSIC -> musicRepository.findById(id).orElse(null)?.let { similarToMusic(it) }
        } ?: throw NoSuchElementException("Media item $id not found")
    }

    private fun similarToMovie(tmdbId: String): List<MediaResponse> {
        val candidateIds = tmdbClient.fetchMovieRecommendationIds(tmdbId.toLong()).map { it.toString() }
        val byTmdbId = movieRepository.findByTmdbIdIn(candidateIds).associateBy { it.tmdbId }
        val matches = candidateIds.mapNotNull { byTmdbId[it] }.take(SIMILAR_MEDIA_LIMIT)
        return mediaResponseAssembler.assembleMovieList(matches)
    }

    private fun similarToTvShow(tmdbId: String): List<MediaResponse> {
        val candidateIds = tmdbClient.fetchTvRecommendationIds(tmdbId.toLong()).map { it.toString() }
        val byTmdbId = tvShowRepository.findByTmdbIdIn(candidateIds).associateBy { it.tmdbId }
        val matches = candidateIds.mapNotNull { byTmdbId[it] }.take(SIMILAR_MEDIA_LIMIT)
        return mediaResponseAssembler.assembleTvShowList(matches)
    }

    private fun similarToGame(igdbId: String): List<MediaResponse> {
        val candidateIds = igdbClient.fetchSimilarGameIds(igdbId.toLong()).map { it.toString() }
        val byIgdbId = gameRepository.findByIgdbIdIn(candidateIds).associateBy { it.igdbId }
        val matches = candidateIds.mapNotNull { byIgdbId[it] }.take(SIMILAR_MEDIA_LIMIT)
        return mediaResponseAssembler.assembleGameList(matches)
    }

    private fun similarToBook(hardcoverId: String): List<MediaResponse> {
        val candidateIds = hardcoverClient.fetchSimilarBookIds(hardcoverId.toLong()).map { it.toString() }
        val byHardcoverId = bookRepository.findByHardcoverIdIn(candidateIds).associateBy { it.hardcoverId }
        val matches = candidateIds.mapNotNull { byHardcoverId[it] }.take(SIMILAR_MEDIA_LIMIT)
        return mediaResponseAssembler.assembleBookList(matches)
    }

    private fun similarToMusic(music: Music): List<MediaResponse> {
        val artistMbids = creditRepository.findByMediaItemId(music.id!!)
            .filter { it.role == RoleType.ARTIST && it.contributor.externalSource == ExternalSourceType.MUSICBRAINZ }
            .mapNotNull { it.contributor.externalSourceId }
        val similarArtistMbids = artistMbids
            .flatMap { artistSimilarityService.similarArtistMbids(it) }
            .distinct()
            .filterNot { it in artistMbids }
        if (similarArtistMbids.isEmpty()) return emptyList()

        val albumIdsByArtist = creditRepository
            .findCreditedItems(MediaCategory.MUSIC, RoleType.ARTIST, ExternalSourceType.MUSICBRAINZ, similarArtistMbids)
            .groupBy({ it.externalId }, { it.mediaItemId })
        val candidateIds = similarArtistMbids.flatMap { albumIdsByArtist[it].orEmpty() }.distinct().filter { it != music.id }
        val byId = musicRepository.findByIdIn(candidateIds).associateBy { it.id }
        val matches = candidateIds.mapNotNull { byId[it] }.take(SIMILAR_MEDIA_LIMIT)
        return mediaResponseAssembler.assembleMusicList(matches)
    }

    companion object {
        const val SIMILAR_MEDIA_LIMIT = 10
    }
}
