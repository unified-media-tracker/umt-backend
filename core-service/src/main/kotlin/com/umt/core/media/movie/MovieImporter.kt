package com.umt.core.media.movie

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.contribution.RoleType
import com.umt.core.media.ContributorCreditService
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaResponseAssembler
import com.umt.core.media.ReleaseDateSyncService
import com.umt.core.media.Genre
import com.umt.core.media.GenreRepository
import com.umt.core.media.movie.tmdb.TmdbCrewMember
import com.umt.core.media.movie.tmdb.parsedReleaseDate
import com.umt.core.media.movie.tmdb.toMovie
import com.umt.core.media.movie.tmdb.TmdbClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Single-item TMDb import for movies - its own bean specifically, so @Transactional actually
 * applies (self-invocation from MovieCatalogSyncScheduler/a controller would bypass Spring's
 * proxy and silently no-op it).
 */
@Component
class MovieImporter(
    private val movieRepository: MovieRepository,
    private val genreRepository: GenreRepository,
    private val tmdbClient: TmdbClient,
    private val mediaResponseAssembler: MediaResponseAssembler,
    private val mediaEventPublisher: MediaEventPublisher,
    private val releaseDateSyncService: ReleaseDateSyncService,
    private val contributorCreditService: ContributorCreditService,
) {

    // Fetching full details even for an already-known movie is deliberate: it's the only way
    // to notice a studio has pushed the release date since yesterday's sync.
    @Transactional
    fun importMovie(tmdbId: Long): MediaItemResponse {
        val existing = movieRepository.findByTmdbId(tmdbId.toString())
        val tmdbMovie = tmdbClient.fetchMovie(tmdbId)

        existing?.let { return updateExisting(it, tmdbMovie.parsedReleaseDate) }

        val movie = tmdbMovie.toMovie()
        movie.genres = resolveGenres(tmdbMovie.genres.map { it.name })
        movie.runtimeMinutes = tmdbMovie.runtime
        val saved = movieRepository.save(movie)
        mediaEventPublisher.publishIfUpcoming(saved)
        creditMovieCrew(saved, tmdbMovie.credits?.crew ?: emptyList())

        return mediaResponseAssembler.assemble(saved)
    }

    // TMDb's crew list has one entry per job (a person can appear multiple times under
    // different jobs/departments), so this can credit the same person twice with different
    // roles — that's correct, not a bug.
    private fun creditMovieCrew(movie: Movie, crew: List<TmdbCrewMember>) {
        crew.filter { it.job == "Director" }.forEach {
            contributorCreditService.credit(movie, ExternalSourceType.TMDB, it.id.toString(), it.name, RoleType.DIRECTOR)
        }
        crew.filter { it.department == "Writing" }
            .distinctBy { it.id }
            .forEach {
                contributorCreditService.credit(movie, ExternalSourceType.TMDB, it.id.toString(), it.name, RoleType.WRITER)
            }
    }

    private fun updateExisting(existing: Movie, incomingDate: java.time.LocalDate?): MediaItemResponse =
        mediaResponseAssembler.assemble(releaseDateSyncService.updateIfChanged(existing, incomingDate, "TMDb"))

    private fun resolveGenres(names: List<String>): MutableSet<Genre> =
        names.map { genreRepository.findByName(it) ?: genreRepository.save(Genre(name = it)) }.toMutableSet()
}
