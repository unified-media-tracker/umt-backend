package com.umt.core.media.music

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.contribution.RoleType
import com.umt.core.media.ContributorCreditService
import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaEventPublisher
import com.umt.core.media.MediaResponseAssembler
import com.umt.core.media.ReleaseDateSyncService
import com.umt.core.media.music.metacritic.UpcomingMusicCandidate
import com.umt.core.media.music.musicbrainz.MusicBrainzClient
import com.umt.core.media.music.musicbrainz.toMusic
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Two-source flow: Metacritic gives {artist, title, date} with no stable id, MusicBrainz then
 * resolves that to a real release-group id - see MetacriticMusicClient's class doc.
 */
@Component
class MusicImporter(
    private val musicRepository: MusicRepository,
    private val musicBrainzClient: MusicBrainzClient,
    private val mediaResponseAssembler: MediaResponseAssembler,
    private val mediaEventPublisher: MediaEventPublisher,
    private val releaseDateSyncService: ReleaseDateSyncService,
    private val contributorCreditService: ContributorCreditService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Returns null when the candidate is a known no-match or a belt-and-suspenders duplicate -
    // both are "nothing new happened", not an error, so the caller just skips them.
    @Transactional
    fun importCandidate(candidate: UpcomingMusicCandidate): MediaItemResponse? {
        // Matched by title only (not title+date): a date change on an already-known release
        // still hits this branch, so it can be compared/updated without spending a throttled
        // MusicBrainz call just to re-discover the same MBID.
        val existingMusic = musicRepository.findByTitleIgnoreCase(candidate.title).firstOrNull()
        if (existingMusic != null) {
            val updated = releaseDateSyncService.updateIfChanged(existingMusic, candidate.releaseDate, "Metacritic")
            return mediaResponseAssembler.assemble(updated)
        }

        val match = musicBrainzClient.findReleaseGroup(candidate.artist, candidate.title)
        if (match == null) {
            log.info("No confident MusicBrainz match for {} - {}, skipping", candidate.artist, candidate.title)
            return null
        }

        // Belt-and-suspenders: the title matching above should already have caught this, but
        // titles can be normalised differently between Metacritic and MusicBrainz.
        if (musicRepository.findByMusicbrainzId(match.id) != null) return null

        val saved = musicRepository.save(match.toMusic(candidate.releaseDate))

        val artistRef = match.artistCredit.firstOrNull()?.artist
        if (artistRef == null) {
            log.warn("MusicBrainz release-group {} had no linked artist id, skipping credit", match.id)
        } else {
            contributorCreditService.credit(saved, ExternalSourceType.MUSICBRAINZ, artistRef.id, artistRef.name, RoleType.ARTIST)
        }
        mediaEventPublisher.requestAnalysisIfUpcoming(saved)

        return mediaResponseAssembler.assemble(saved)
    }
}
