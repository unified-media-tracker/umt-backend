package com.umt.core.media

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.contribution.*
import com.umt.core.media.igdb.IgdbClient
import com.umt.core.media.igdb.parsedReleaseDate as parsedReleaseDateFromIgdb
import com.umt.core.media.igdb.toMediaItem as toMediaItemFromIgdb
import com.umt.core.media.metacritic.MetacriticAlbumsClient
import com.umt.core.media.musicbrainz.MusicBrainzClient
import com.umt.core.media.musicbrainz.MusicBrainzReleaseGroup
import com.umt.core.media.musicbrainz.toMediaItem as toMediaItemFromMusicBrainz
import com.umt.core.media.tmdb.TmdbCatalogImporter
import com.umt.core.media.tmdb.TmdbClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MediaServiceImpl(
    private val mediaItemRepository: MediaRepository,
    private val contributorRepository: ContributorRepository,
    private val creditRepository: CreditRepository,
    private val tmdbClient: TmdbClient,
    private val tmdbCatalogImporter: TmdbCatalogImporter,
    private val metacriticAlbumsClient: MetacriticAlbumsClient,
    private val igdbClient: IgdbClient,
    private val musicBrainzClient: MusicBrainzClient,
    private val mediaEventPublisher: MediaEventPublisher,
    private val releaseDateSyncService: ReleaseDateSyncService,
    private val mediaMapper: MediaMapper,
) : MediaService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun importMovieFromTmdb(tmdbId: Long): MediaItemResponse = tmdbCatalogImporter.importMovie(tmdbId)

    override fun importTvShowFromTmdb(tmdbId: Long): MediaItemResponse = tmdbCatalogImporter.importTvShow(tmdbId)

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

    override fun syncUpcomingTvSeries(): List<MediaItemResponse> {
        val ids = tmdbClient.fetchUpcomingTvShowIds()
        val results = mutableListOf<MediaItemResponse>()

        for (id in ids) {
            try {
                results.add(tmdbCatalogImporter.importTvShow(id))
            } catch (ex: Exception) {
                log.error("Failed to import upcoming tv show tmdbId={}, skipping it this run", id, ex)
            }
        }

        log.info("TV sync: {} discovered from TMDb", ids.size)
        return results
    }

    // Deliberately not @Transactional: MusicBrainz enforces ~1 request/second, so this loop
    // can run for a while on a big batch. Each mediaItemRepository.save() is already
    // transactional on its own (Spring Data JPA), which is also the right granularity here -
    // one bad candidate shouldn't roll back albums already imported earlier in the same run.
    override fun syncUpcomingAlbums(): List<MediaItemResponse> {
        val discovered = metacriticAlbumsClient.fetchUpcomingAlbums()
        val results = mutableListOf<MediaItemResponse>()

        for (candidate in discovered) {
            try {
                // Matched by title only (not title+date): a date change on an already-known
                // album still hits this branch, so it can be compared/updated without spending
                // a throttled MusicBrainz call just to re-discover the same MBID.
                val existingAlbum = mediaItemRepository.findFirstByMediaTypeAndTitleIgnoreCase(
                    MediaType.MUSIC, candidate.title,
                )
                if (existingAlbum != null) {
                    val updated = releaseDateSyncService.updateIfChanged(existingAlbum, candidate.releaseDate, "Metacritic")
                    results.add(mediaMapper.toResponse(updated))
                    continue
                }

                val match = musicBrainzClient.findReleaseGroup(candidate.artist, candidate.title)
                if (match == null) {
                    log.info("No confident MusicBrainz match for {} - {}, skipping", candidate.artist, candidate.title)
                    continue
                }

                // Belt-and-suspenders: the title matching above should already have caught this,
                // but titles can be normalised differently between Metacritic and MusicBrainz.
                val existingByMbid = mediaItemRepository.findByExternalSourceAndExternalSourceId(
                    ExternalSourceType.MUSICBRAINZ, match.id,
                )
                if (existingByMbid != null) continue

                val mediaItem = match.toMediaItemFromMusicBrainz(candidate.releaseDate)
                val saved = mediaItemRepository.save(mediaItem)

                linkArtistCredit(match, saved)
                mediaEventPublisher.publishIfUpcoming(saved)
                results.add(mediaMapper.toResponse(saved))
            } catch (ex: Exception) {
                log.error("Failed to process candidate {} - {}, skipping it this run", candidate.artist, candidate.title, ex)
            }
        }

        log.info("Album sync: {} discovered from Metacritic", discovered.size)
        return results
    }

    // Find-or-create the artist by MusicBrainz artist MBID (not by name — see Contributor's
    // externalSource comment) and credit them on this album, so an artist profile page can
    // later just query Credit by contributor instead of matching album titles by name.
    private fun linkArtistCredit(match: MusicBrainzReleaseGroup, mediaItem: MediaItem) {
        val artistRef = match.artistCredit.firstOrNull()?.artist ?: run {
            log.warn("MusicBrainz release-group {} had no linked artist id, skipping credit", match.id)
            return
        }

        val contributor = contributorRepository.findByExternalSourceAndExternalSourceId(
            ExternalSourceType.MUSICBRAINZ, artistRef.id,
        ) ?: contributorRepository.save(
            Contributor(
                contributorType = ContributorType.PERSON,
                name = artistRef.name,
                externalSource = ExternalSourceType.MUSICBRAINZ,
                externalSourceId = artistRef.id,
            )
        )

        creditRepository.save(Credit(mediaItem = mediaItem, contributor = contributor, role = RoleType.ARTIST))
    }

    // IGDB is both the discovery and the identity source in one call (unlike the Metacritic+
    // MusicBrainz split for albums), so this is simpler: no per-candidate throttling needed,
    // IGDB's own limit is 4req/s/8 concurrent, and we only make one query per run here.
    override fun syncUpcomingGames(): List<MediaItemResponse> {
        val games = igdbClient.fetchUpcomingGames()
        val results = mutableListOf<MediaItemResponse>()

        for (game in games) {
            try {
                val existing = mediaItemRepository.findByExternalSourceAndExternalSourceId(
                    ExternalSourceType.IGDB, game.id.toString(),
                )
                if (existing != null) {
                    val updated = releaseDateSyncService.updateIfChanged(existing, game.parsedReleaseDateFromIgdb, "IGDB")
                    results.add(mediaMapper.toResponse(updated))
                    continue
                }

                val mediaItem = game.toMediaItemFromIgdb()
                val saved = mediaItemRepository.save(mediaItem)

                mediaEventPublisher.publishIfUpcoming(saved)
                results.add(mediaMapper.toResponse(saved))
            } catch (ex: Exception) {
                log.error("Failed to process IGDB game {} - {}, skipping it this run", game.id, game.name, ex)
            }
        }

        log.info("Game sync: {} fetched from IGDB", games.size)
        return results
    }

    override fun getUserRecommendations(userId: Long): List<MediaItemResponse> {
        return mediaMapper.toListResponse(
            mediaItems = mediaItemRepository.fndRandomMediaItemsLimit(RANDOM_MEDIA_ITEMS_LIMIT)
        )
    }

    companion object {
        const val RANDOM_MEDIA_ITEMS_LIMIT = 10
    }
}
