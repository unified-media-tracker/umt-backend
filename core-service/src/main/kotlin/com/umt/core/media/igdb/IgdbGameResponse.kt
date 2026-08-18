package com.umt.core.media.igdb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class IgdbGame(
    val id: Long,
    val name: String,
    @JsonProperty("first_release_date") val firstReleaseDate: Long?,
    val summary: String?,
    val cover: IgdbCover?,
    @JsonProperty("involved_companies") val involvedCompanies: List<IgdbInvolvedCompany> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IgdbCover(
    @JsonProperty("image_id") val imageId: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IgdbInvolvedCompany(
    val company: IgdbCompany?,
    val developer: Boolean = false,
    val publisher: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IgdbCompany(
    val id: Long,
    val name: String,
)
