package com.umt.core.media.music.listenbrainz

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ListenBrainzSimilarArtist(
    @JsonProperty("artist_mbid") val artistMbid: String,
    val name: String,
    val score: Int,
)
