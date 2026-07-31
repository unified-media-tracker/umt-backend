package com.umt.core.media.musicbrainz

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicBrainzSearchResponse(
    @JsonProperty("release-groups") val releaseGroups: List<MusicBrainzReleaseGroup> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicBrainzReleaseGroup(
    val id: String,
    val title: String,
    val score: Int,
    @JsonProperty("primary-type") val primaryType: String?,
    @JsonProperty("first-release-date") val firstReleaseDate: String?,
    @JsonProperty("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicBrainzArtistCredit(
    val name: String,
    val artist: MusicBrainzArtistRef? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicBrainzArtistRef(
    val id: String,
    val name: String,
)
