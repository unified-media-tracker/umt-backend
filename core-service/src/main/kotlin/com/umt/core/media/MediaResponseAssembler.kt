package com.umt.core.media

import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.TrendDirection as ApiTrendDirection
import com.umt.core.media.book.Book
import com.umt.core.media.book.BookMapper
import com.umt.core.contribution.ContributorMapper
import com.umt.core.contribution.CreditRepository
import com.umt.core.media.game.Game
import com.umt.core.media.game.GameMapper
import com.umt.core.media.music.Music
import com.umt.core.media.music.MusicMapper
import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieMapper
import com.umt.core.rumor.RumorSnapshotRepository
import com.umt.core.rumor.TrendDirection
import com.umt.core.media.tvshow.TvShow
import com.umt.core.media.tvshow.TvShowMapper
import org.springframework.stereotype.Component

/**
 * One `assemble` overload per type - each type's own Mapper builds the base MediaItemResponse
 * (title, dates, its own genres...), this fills in the two things that live outside every type's
 * own table: contributors (credit) and the delay signal (rumor_snapshot). Both are loosely
 * referenced by (id, mediaCategory) - see Credit's class doc - so this is the one place that reads them.
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
    fun assemble(movie: Movie): MediaItemResponse = enrich(movie, movieMapper.toResponse(movie))
    fun assemble(tvShow: TvShow): MediaItemResponse = enrich(tvShow, tvShowMapper.toResponse(tvShow))
    fun assemble(game: Game): MediaItemResponse = enrich(game, gameMapper.toResponse(game))
    fun assemble(book: Book): MediaItemResponse = enrich(book, bookMapper.toResponse(book))
    fun assemble(music: Music): MediaItemResponse = enrich(music, musicMapper.toResponse(music))

    // Distinct names, not an assembleList(...) overload set: List<Movie>/List<TvShow>/... all
    // erase to the same raw List on the JVM, so the overloads would clash at the bytecode level
    // even though Kotlin's own (pre-erasure) overload resolution tells them apart fine. @JvmName
    // would normally disambiguate this, but kotlin-spring's all-open plugin makes @Component
    // members open, and @JvmName isn't allowed on open members - so distinct names it is.
    fun assembleMovieList(movies: List<Movie>): List<MediaItemResponse> = enrichBatch(movies, movieMapper::toResponse)
    fun assembleTvShowList(tvShows: List<TvShow>): List<MediaItemResponse> = enrichBatch(tvShows, tvShowMapper::toResponse)
    fun assembleGameList(games: List<Game>): List<MediaItemResponse> = enrichBatch(games, gameMapper::toResponse)
    fun assembleBookList(books: List<Book>): List<MediaItemResponse> = enrichBatch(books, bookMapper::toResponse)
    fun assembleMusicList(releases: List<Music>): List<MediaItemResponse> = enrichBatch(releases, musicMapper::toResponse)

    private fun enrich(item: MediaItem, base: MediaItemResponse): MediaItemResponse {
        val id = item.id!!
        val contributors = creditRepository.findByMediaItemId(id).map { contributorMapper.toContributorResponse(it) }
        val latestSnapshot = rumorSnapshotRepository.findByMediaItemIdOrderByComputedAtDesc(id).firstOrNull()

        return base.copy(
            contributors = contributors,
            latestDelayProbability = latestSnapshot?.delayProbability,
            latestConfidenceTrend = latestSnapshot?.confidenceTrend?.toApiModel(),
        )
    }

    // One query per lookup table regardless of list size - for a catalog-sized response
    // (the cross-type list endpoint), not assemble()'s handful of admin-import results.
    private fun <T : MediaItem> enrichBatch(items: List<T>, toBase: (T) -> MediaItemResponse): List<MediaItemResponse> {
        if (items.isEmpty()) return emptyList()
        val ids = items.mapNotNull { it.id }

        val contributorsByItemId = creditRepository.findByMediaItemIdIn(ids)
            .groupBy { it.mediaItemId }
            .mapValues { (_, credits) -> credits.map { contributorMapper.toContributorResponse(it) } }
        val latestSnapshotByItemId = rumorSnapshotRepository.findByMediaItemIdIn(ids)
            .groupBy { it.mediaItemId }
            .mapValues { (_, snapshots) -> snapshots.maxByOrNull { it.computedAt } }

        return items.map { item ->
            toBase(item).copy(
                contributors = contributorsByItemId[item.id].orEmpty(),
                latestDelayProbability = latestSnapshotByItemId[item.id]?.delayProbability,
                latestConfidenceTrend = latestSnapshotByItemId[item.id]?.confidenceTrend?.toApiModel(),
            )
        }
    }

    private fun TrendDirection.toApiModel(): ApiTrendDirection = when (this) {
        TrendDirection.RISING -> ApiTrendDirection.RISING
        TrendDirection.FALLING -> ApiTrendDirection.FALLING
        TrendDirection.STABLE -> ApiTrendDirection.STABLE
    }
}
