package com.umt.core.media.book.hardcover

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class HardcoverGraphQlResponse(
    val data: HardcoverData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HardcoverData(
    val books: List<HardcoverBook> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HardcoverBook(
    val id: Long,
    val title: String,
    val description: String?,
    val slug: String?,
    // Aliased from release_date to releaseDate in the GraphQL query itself (HardcoverClient)
    val releaseDate: String?,
    val image: HardcoverImage?,
    val contributions: List<HardcoverContribution> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HardcoverImage(val url: String?)

// contribution is null, for the primary author, or a string like "Illustrator"/"Translator"
@JsonIgnoreProperties(ignoreUnknown = true)
data class HardcoverContribution(val author: HardcoverAuthor?, val contribution: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HardcoverAuthor(val id: Long, val name: String)

// A minimal shape for the similar-books lookup - just the id list Hardcover already caches,
// not full book records (verified live: cached_similar_book_ids is a plain array of ids).
@JsonIgnoreProperties(ignoreUnknown = true)
data class HardcoverSimilarBooksResponse(
    val data: HardcoverSimilarBooksData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HardcoverSimilarBooksData(
    val books: List<HardcoverSimilarBookIds> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HardcoverSimilarBookIds(
    val id: Long,
    val cachedSimilarBookIds: List<Long> = emptyList(),
)
