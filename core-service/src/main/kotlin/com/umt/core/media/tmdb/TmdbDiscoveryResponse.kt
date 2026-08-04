package com.umt.core.media.tmdb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Shared shape for /movie/upcoming and /discover/tv — both are paginated lists where we only
 * need the id to hand off to the existing single-item import (which already knows how to
 * fetch full details, wire genres, and dedupe). totalPages drives full pagination in TmdbClient.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbDiscoveryResponse(
    @JsonProperty("total_pages") val totalPages: Int = 1,
    val results: List<TmdbDiscoveryResult> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbDiscoveryResult(
    val id: Long,
)
