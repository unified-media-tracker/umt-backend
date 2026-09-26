package com.umt.core.media.music.listenbrainz

import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ListenBrainzClient(
    private val listenBrainzRestClient: RestClient,
    private val properties: ListenBrainzProperties,
) {
    // Artists whose listeners overlap with this one's, best match first. Empty when ListenBrainz
    // has no listening data for the artist.
    fun fetchSimilarArtists(artistMbid: String): List<ListenBrainzSimilarArtist> =
        listenBrainzRestClient.get()
            .uri {
                it.path("/similar-artists/json")
                    .queryParam("artist_mbids", artistMbid)
                    .queryParam("algorithm", properties.similarArtistsAlgorithm)
                    .build()
            }
            .retrieve()
            .body(object : ParameterizedTypeReference<List<ListenBrainzSimilarArtist>>() {})
            ?: emptyList()
}
