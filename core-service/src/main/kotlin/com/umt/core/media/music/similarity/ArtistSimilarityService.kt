package com.umt.core.media.music.similarity

import com.umt.core.media.music.listenbrainz.ListenBrainzClient
import com.umt.core.media.music.listenbrainz.ListenBrainzSimilarArtist
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.time.Instant

@Service
class ArtistSimilarityService(
    private val listenBrainzClient: ListenBrainzClient,
    private val artistSimilarityRepository: ArtistSimilarityRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * MBIDs of the artists most similar to [artistMbid], best match first. Read from the DB;
     * ListenBrainz is only asked for an artist never looked up, or last looked up over
     * REFRESH_AFTER ago.
     */
    fun similarArtistMbids(artistMbid: String, now: Instant = Instant.now()): List<String> {
        val stored = artistSimilarityRepository.findById(artistMbid).orElse(null)
        if (stored != null && stored.fetchedAt.isAfter(now.minus(REFRESH_AFTER))) return stored.mbidsBestFirst()

        val fetched = fetch(artistMbid) ?: return stored?.mbidsBestFirst().orEmpty()

        val similar = fetched
            .filter { it.artistMbid != artistMbid }
            .distinctBy { it.artistMbid }
            .sortedByDescending { it.score }
            .map { SimilarArtist(it.artistMbid, it.name, it.score) }
        store(artistMbid, similar, now)

        return similar.map { it.mbid }
    }

    // null means the lookup failed, which is not the same as ListenBrainz having nothing for the
    // artist - that comes back as an empty list and is stored.
    private fun fetch(artistMbid: String): List<ListenBrainzSimilarArtist>? =
        try {
            listenBrainzClient.fetchSimilarArtists(artistMbid)
        } catch (ex: RestClientException) {
            log.warn("ListenBrainz similar-artists lookup failed for {}: {}", artistMbid, ex.message)
            null
        }

    // A failed save only costs another lookup next time, so it must not fail the request.
    private fun store(artistMbid: String, similar: List<SimilarArtist>, now: Instant) {
        try {
            artistSimilarityRepository.save(ArtistSimilarity(artistMbid, now).apply { similarArtists = similar.toMutableList() })
        } catch (ex: DataAccessException) {
            log.warn("Could not store similar artists for {}: {}", artistMbid, ex.message)
        }
    }

    private fun ArtistSimilarity.mbidsBestFirst(): List<String> =
        similarArtists.sortedByDescending { it.score }.map { it.mbid }

    companion object {
        val REFRESH_AFTER: Duration = Duration.ofDays(30)
    }
}
