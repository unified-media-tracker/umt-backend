package com.umt.core.media.tvshow

import com.umt.api.generated.model.TvShowResponse
import com.umt.core.contribution.RoleType
import com.umt.core.media.ContributorCreditService
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaResponseAssembler
import com.umt.core.media.ReleaseDateSyncService
import com.umt.core.media.Genre
import com.umt.core.media.GenreRepository
import com.umt.core.media.movie.tmdb.TmdbClient
import com.umt.core.media.tvshow.tmdb.parsedReleaseDate
import com.umt.core.media.tvshow.tmdb.toTvShow
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class TvShowImporter(
    private val tvShowRepository: TvShowRepository,
    private val genreRepository: GenreRepository,
    private val tmdbClient: TmdbClient,
    private val mediaResponseAssembler: MediaResponseAssembler,
    private val mediaEventPublisher: MediaEventPublisher,
    private val releaseDateSyncService: ReleaseDateSyncService,
    private val contributorCreditService: ContributorCreditService,
) {

    @Transactional
    fun importTvShow(tmdbId: Long): TvShowResponse {
        val existing = tvShowRepository.findByTmdbId(tmdbId.toString())
        val tmdbShow = tmdbClient.fetchTvShow(tmdbId)

        existing?.let { return updateExisting(it, tmdbShow.parsedReleaseDate) }

        val tvShow = tmdbShow.toTvShow()
        tvShow.genres = resolveGenres(tmdbShow.genres.map { it.name })
        val saved = tvShowRepository.save(tvShow)
        mediaEventPublisher.requestAnalysisIfUpcoming(saved)

        // TMDb has no "creator" role of its own in our vocabulary — DIRECTOR is the closest
        // existing fit for "the person(s) who made this show" rather than adding a new role
        // just for this one field.
        tmdbShow.createdBy.forEach {
            contributorCreditService.credit(saved, ExternalSourceType.TMDB, it.id.toString(), it.name, RoleType.DIRECTOR)
        }

        return mediaResponseAssembler.assemble(saved)
    }

    private fun updateExisting(existing: TvShow, incomingDate: LocalDate?): TvShowResponse =
        mediaResponseAssembler.assemble(releaseDateSyncService.updateIfChanged(existing, incomingDate, "TMDb"))

    private fun resolveGenres(names: List<String>): MutableSet<Genre> =
        names.map { genreRepository.findByName(it) ?: genreRepository.save(Genre(name = it)) }.toMutableSet()
}
