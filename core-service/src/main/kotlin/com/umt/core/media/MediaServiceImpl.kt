package com.umt.core.media

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.contribution.*
import com.umt.core.media.genre.Genre
import com.umt.core.media.genre.GenreRepository
import com.umt.core.media.metacritic.MetacriticAlbumsClient
import com.umt.core.media.musicbrainz.MusicBrainzClient
import com.umt.core.media.musicbrainz.MusicBrainzReleaseGroup
import com.umt.core.media.tmdb.TmdbClient
import com.umt.core.media.tmdb.toMediaItem
import com.umt.core.rumor.RabbitMQConfig
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.umt.core.media.musicbrainz.toMediaItem as toMediaItemFromMusicBrainz

@Service
class MediaServiceImpl(
    private val mediaItemRepository: MediaRepository,
    private val movieDetailsRepository: MovieDetailsRepository,
    private val genreRepository: GenreRepository,
    private val contributorRepository: ContributorRepository,
    private val creditRepository: CreditRepository,
    private val tmdbClient: TmdbClient,
    private val metacriticAlbumsClient: MetacriticAlbumsClient,
    private val musicBrainzClient: MusicBrainzClient,
    private val mediaMapper: MediaMapper,
    private val rabbitTemplate: RabbitTemplate,
) : MediaService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun importMovieFromTmdb(tmdbId: Long): MediaItemResponse {
        val existing = mediaItemRepository.findByExternalSourceAndExternalSourceId(
            ExternalSourceType.TMDB, tmdbId.toString(),
        )
        if (existing != null) return mediaMapper.toResponse(existing)

        val tmdbMovie = tmdbClient.fetchMovie(tmdbId)
        val mediaItem = tmdbMovie.toMediaItem()

        mediaItem.genres = tmdbMovie.genres
            .map { genreRepository.findByName(it.name) ?: genreRepository.save(Genre(name = it.name)) }
            .toMutableSet()

        val saved = mediaItemRepository.save(mediaItem)

        // publishing event for ai-analyser
        if (saved.releaseDateStatus != ReleaseStatus.RELEASED) addToQueue(mediaItem)

        movieDetailsRepository.save(
            MovieDetails(
                mediaItem = saved,
                runtimeMinutes = tmdbMovie.runtime,
            )
        )

        return mediaMapper.toResponse(saved)
    }

    // Deliberately not @Transactional: MusicBrainz enforces ~1 request/second, so this loop
    // can run for a while on a big batch. Each mediaItemRepository.save() is already
    // transactional on its own (Spring Data JPA), which is also the right granularity here -
    // one bad candidate shouldn't roll back albums already imported earlier in the same run.
    override fun syncUpcomingAlbums(): List<MediaItemResponse> {
        val discovered = metacriticAlbumsClient.fetchUpcomingAlbums()
        val imported = mutableListOf<MediaItem>()

        for (candidate in discovered) {
            try {
                // Cheap local check first - skips the throttled MusicBrainz call for the ~99%
                // of each run that's the same overlapping window we already resolved yesterday.
                val alreadyImported = mediaItemRepository.existsByMediaTypeAndTitleIgnoreCaseAndReleaseDate(
                    MediaType.MUSIC, candidate.title, candidate.releaseDate,
                )
                if (alreadyImported) continue

                val match = musicBrainzClient.findReleaseGroup(candidate.artist, candidate.title)
                if (match == null) {
                    log.info("No confident MusicBrainz match for {} - {}, skipping", candidate.artist, candidate.title)
                    continue
                }

                val existing = mediaItemRepository.findByExternalSourceAndExternalSourceId(
                    ExternalSourceType.MUSICBRAINZ, match.id,
                )
                if (existing != null) continue

                val mediaItem = match.toMediaItemFromMusicBrainz(candidate.releaseDate)
                val saved = mediaItemRepository.save(mediaItem)

                linkArtistCredit(match, saved)

                if (saved.releaseDateStatus != ReleaseStatus.RELEASED) addToQueue(mediaItem)
                imported.add(saved)
            } catch (ex: Exception) {
                // One bad candidate (network hiccup, unexpected data shape, whatever) shouldn't
                // cost us everything already imported earlier in this same run.
                log.error("Failed to process candidate {} - {}, skipping it this run", candidate.artist, candidate.title, ex)
            }
        }

        log.info("Album sync: {} discovered, {} newly imported", discovered.size, imported.size)
        return mediaMapper.toListResponse(imported)
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

    fun addToQueue(mediaItem: MediaItem) =
        mediaItem.id?.let {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE,
                RabbitMQConfig.MEDIA_IMPORTED_ROUTING_KEY,
                MediaImportedEvent(
                    mediaItemId = it,
                    title = mediaItem.title
                )
            )
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