package com.umt.core.media

import com.umt.api.generated.model.BookResponse
import com.umt.api.generated.model.GameResponse
import com.umt.api.generated.model.MovieResponse
import com.umt.api.generated.model.MusicResponse
import com.umt.api.generated.model.TvShowResponse
import com.umt.api.generated.model.TrendDirection as ApiTrendDirection
import com.umt.core.contribution.ContributorMapper
import com.umt.core.contribution.CreditRepository
import com.umt.core.media.book.Book
import com.umt.core.media.book.BookMapper
import com.umt.core.media.game.Game
import com.umt.core.media.game.GameMapper
import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieMapper
import com.umt.core.media.music.Music
import com.umt.core.media.music.MusicMapper
import com.umt.core.media.tvshow.TvShow
import com.umt.core.media.tvshow.TvShowMapper
import com.umt.core.rumor.RumorSnapshotRepository
import com.umt.core.rumor.TrendDirection
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Builds each type's own response through its mapper, handing it the credits and delay signal
 * that lives outside the media tables (both loosely referenced by id and mediaCategory).
 */
@Component
class MediaResponseAssembler(
    private val movieMapper: MovieMapper,
    private val tvShowMapper: TvShowMapper,
    private val gameMapper: GameMapper,
    private val bookMapper: BookMapper,
    private val musicMapper: MusicMapper,
    private val contributorMapper: ContributorMapper,
    private val creditRepository: CreditRepository,
    private val rumorSnapshotRepository: RumorSnapshotRepository,
) {
    fun assemble(movie: Movie): MovieResponse = movieMapper.toResponse(movie, extrasOf(movie))
    fun assemble(tvShow: TvShow): TvShowResponse = tvShowMapper.toResponse(tvShow, extrasOf(tvShow))
    fun assemble(game: Game): GameResponse = gameMapper.toResponse(game, extrasOf(game))
    fun assemble(book: Book): BookResponse = bookMapper.toResponse(book, extrasOf(book))
    fun assemble(music: Music): MusicResponse = musicMapper.toResponse(music, extrasOf(music))

    fun assembleMovieList(movies: List<Movie>): List<MovieResponse> = assembleAll(movies, movieMapper::toResponse)
    fun assembleTvShowList(tvShows: List<TvShow>): List<TvShowResponse> = assembleAll(tvShows, tvShowMapper::toResponse)
    fun assembleGameList(games: List<Game>): List<GameResponse> = assembleAll(games, gameMapper::toResponse)
    fun assembleBookList(books: List<Book>): List<BookResponse> = assembleAll(books, bookMapper::toResponse)
    fun assembleMusicList(releases: List<Music>): List<MusicResponse> = assembleAll(releases, musicMapper::toResponse)

    private fun extrasOf(item: MediaItem): ResponseExtras {
        val id = item.id!!
        val contributors = creditRepository.findByMediaItemId(id).map { contributorMapper.toContributorResponse(it) }
        val latestSnapshot = rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(id).firstOrNull()

        return ResponseExtras(contributors, latestSnapshot?.delayProbability, latestSnapshot?.confidenceTrend?.toApiModel())
    }

    // One query per table, regardless of list size, for catalogue-sized responses.
    private fun <T : MediaItem, R> assembleAll(items: List<T>, toResponse: (T, ResponseExtras) -> R): List<R> {
        if (items.isEmpty()) return emptyList()
        val extrasById = extrasByItemId(items.mapNotNull { it.id })

        return items.map { toResponse(it, extrasById[it.id] ?: ResponseExtras.NONE) }
    }

    private fun extrasByItemId(ids: List<UUID>): Map<UUID, ResponseExtras> {
        val contributorsByItemId = creditRepository.findByMediaItemIdIn(ids)
            .groupBy { it.mediaItemId }
            .mapValues { (_, credits) -> credits.map { contributorMapper.toContributorResponse(it) } }
        val latestSnapshotByItemId = rumorSnapshotRepository.findByMediaItemIdIn(ids)
            .groupBy { it.mediaItemId }
            .mapValues { (_, snapshots) -> snapshots.maxByOrNull { it.computedAt } }

        return ids.associateWith { id ->
            ResponseExtras(
                contributorsByItemId[id].orEmpty(),
                latestSnapshotByItemId[id]?.delayProbability,
                latestSnapshotByItemId[id]?.confidenceTrend?.toApiModel(),
            )
        }
    }

    private fun TrendDirection.toApiModel(): ApiTrendDirection = when (this) {
        TrendDirection.RISING -> ApiTrendDirection.RISING
        TrendDirection.FALLING -> ApiTrendDirection.FALLING
        TrendDirection.STABLE -> ApiTrendDirection.STABLE
    }
}
