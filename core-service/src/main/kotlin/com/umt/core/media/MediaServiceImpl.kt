package com.umt.core.media

import com.umt.api.generated.model.MediaResponse
import com.umt.api.generated.model.MediaSortOption
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.core.media.MediaSpecifications.listing
import com.umt.core.media.book.BookRepository
import com.umt.core.media.game.GameRepository
import com.umt.core.media.music.MusicRepository
import com.umt.core.media.movie.MovieRepository
import com.umt.core.media.tvshow.TvShowRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Service
class MediaServiceImpl(
    private val movieRepository: MovieRepository,
    private val tvShowRepository: TvShowRepository,
    private val gameRepository: GameRepository,
    private val bookRepository: BookRepository,
    private val musicRepository: MusicRepository,
    private val mediaResponseAssembler: MediaResponseAssembler,
    private val mediaMapper: MediaMapper,
) : MediaService {

    override fun listMedia(
        mediaCategory: ApiMediaCategory,
        status: ApiReleaseStatus?,
        sort: MediaSortOption?,
        releaseDateFrom: LocalDate?,
    ): List<MediaResponse> {
        val domainStatus = status?.let { mediaMapper.toDomainReleaseStatus(it) }

        val responses = when (mediaMapper.toDomainMediaCategory(mediaCategory)) {
            MediaCategory.MOVIE -> mediaResponseAssembler.assembleMovieList(
                movieRepository.findAll(listing(domainStatus, releaseDateFrom))
            )
            MediaCategory.TV_SHOW -> mediaResponseAssembler.assembleTvShowList(
                tvShowRepository.findAll(listing(domainStatus, releaseDateFrom))
            )
            MediaCategory.GAME -> mediaResponseAssembler.assembleGameList(
                gameRepository.findAll(listing(domainStatus, releaseDateFrom))
            )
            MediaCategory.BOOK -> mediaResponseAssembler.assembleBookList(
                bookRepository.findAll(listing(domainStatus, releaseDateFrom))
            )
            MediaCategory.MUSIC -> mediaResponseAssembler.assembleMusicList(
                musicRepository.findAll(listing(domainStatus, releaseDateFrom))
            )
        }

        return responses.sortedForResponse(sort)
    }

    // mediaCategory given: one table, one query. mediaCategory omitted: id alone doesn't say which of
    // the five to look in, so this tries each in turn and takes the first hit - five tries the
    // worst case, but only for a title with no match at all; movie/tv_show/game are the most
    // likely hits, so they're checked first.
    override fun getMediaById(id: UUID, mediaCategory: ApiMediaCategory?): MediaResponse {
        if (mediaCategory != null) return getMediaByKnownType(id, mediaMapper.toDomainMediaCategory(mediaCategory))

        movieRepository.findById(id).orElse(null)?.let { return mediaResponseAssembler.assemble(it) }
        tvShowRepository.findById(id).orElse(null)?.let { return mediaResponseAssembler.assemble(it) }
        gameRepository.findById(id).orElse(null)?.let { return mediaResponseAssembler.assemble(it) }
        bookRepository.findById(id).orElse(null)?.let { return mediaResponseAssembler.assemble(it) }
        musicRepository.findById(id).orElse(null)?.let { return mediaResponseAssembler.assemble(it) }
        throw NoSuchElementException("Media item $id not found")
    }

    private fun getMediaByKnownType(id: UUID, mediaCategory: MediaCategory): MediaResponse {
        val response = when (mediaCategory) {
            MediaCategory.MOVIE -> movieRepository.findById(id).orElse(null)?.let { mediaResponseAssembler.assemble(it) }
            MediaCategory.TV_SHOW -> tvShowRepository.findById(id).orElse(null)?.let { mediaResponseAssembler.assemble(it) }
            MediaCategory.GAME -> gameRepository.findById(id).orElse(null)?.let { mediaResponseAssembler.assemble(it) }
            MediaCategory.BOOK -> bookRepository.findById(id).orElse(null)?.let { mediaResponseAssembler.assemble(it) }
            MediaCategory.MUSIC -> musicRepository.findById(id).orElse(null)?.let { mediaResponseAssembler.assemble(it) }
        }
        return response ?: throw NoSuchElementException("Media item $id not found")
    }

    // Assembles every item across all five tables before picking RANDOM_MEDIA_ITEMS_LIMIT of
    // them - wasteful in principle, fine in practice at this catalogue's size, and it avoids
    // needing runtime type-dispatch anywhere else in the codebase for the one heterogeneous list.
    override fun getUserRecommendations(userId: Long): List<MediaResponse> {
        val pool = mediaResponseAssembler.assembleMovieList(movieRepository.findAll()) +
            mediaResponseAssembler.assembleTvShowList(tvShowRepository.findAll()) +
            mediaResponseAssembler.assembleGameList(gameRepository.findAll()) +
            mediaResponseAssembler.assembleBookList(bookRepository.findAll()) +
            mediaResponseAssembler.assembleMusicList(musicRepository.findAll())

        return pool.shuffled().take(RANDOM_MEDIA_ITEMS_LIMIT)
    }

    // TBA/unscored items sort to the end regardless of direction, rather than being read as
    // "releases today" or "0% risk" - the same sentinel values the frontend uses for the same reason.
    private fun List<MediaResponse>.sortedForResponse(sort: MediaSortOption?): List<MediaResponse> = when (sort) {
        MediaSortOption.DELAY_RISK -> sortedByDescending { it.latestDelayProbability ?: BigDecimal.valueOf(-1) }
        MediaSortOption.POPULARITY -> sortedByDescending { it.popularityScore }
        MediaSortOption.RELEASE_DATE, null -> sortedBy { it.releaseDate ?: LocalDate.MAX }
    }

    companion object {
        const val RANDOM_MEDIA_ITEMS_LIMIT = 10
    }
}
