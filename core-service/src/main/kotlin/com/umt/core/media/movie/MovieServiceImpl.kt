package com.umt.core.media.movie

import arrow.core.Either
import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.media.tmdb.TmdbCatalogImporter
import com.umt.core.media.tmdb.TmdbClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MovieServiceImpl(
    private val tmdbClient: TmdbClient,
    private val tmdbCatalogImporter: TmdbCatalogImporter,
): MovieService {

    private val log = LoggerFactory.getLogger(javaClass)
    override fun getDetailsById(id: String): Either<Any, MediaItemResponse> {
        TODO("Not yet implemented")
    }

    override fun importMovieFromTmdb(tmdbId: Long): MediaItemResponse = tmdbCatalogImporter.importMovie(tmdbId)

    // Calls tmdbCatalogImporter directly (a different bean) rather than this.importMovieFromTmdb -
    // self-invocation would bypass Spring's proxy and silently drop @Transactional. See
    // TmdbCatalogImporter's class doc for the full story.
    override fun syncUpcomingMovies(): List<MediaItemResponse> {
        val ids = tmdbClient.fetchUpcomingMovieIds()
        val results = mutableListOf<MediaItemResponse>()

        for (id in ids) {
            try {
                results.add(tmdbCatalogImporter.importMovie(id))
            } catch (ex: Exception) {
                log.error("Failed to import upcoming movie tmdbId={}, skipping it this run", id, ex)
            }
        }

        log.info("Movie sync: {} discovered from TMDb", ids.size)
        return results
    }

}