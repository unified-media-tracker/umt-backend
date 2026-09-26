package com.umt.core.media.music.similarity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant

/**
 * When ListenBrainz was last asked which artists are similar to this one. An empty similarArtists
 * means it was asked and had nothing, which is not the same as never asked.
 */
@Entity
@Table(name = ArtistSimilarity.TABLE_NAME)
class ArtistSimilarity(
    @Id
    @field:Column(name = ARTIST_MBID_COLUMN, length = 36)
    var artistMbid: String,

    @field:Column(name = FETCHED_AT_COLUMN, nullable = false)
    var fetchedAt: Instant,
) {
    @ElementCollection
    @CollectionTable(name = SimilarArtist.TABLE_NAME, joinColumns = [JoinColumn(name = SimilarArtist.ARTIST_MBID_COLUMN)])
    var similarArtists: MutableList<SimilarArtist> = mutableListOf()

    companion object {
        const val TABLE_NAME = "artist_similarity"
        const val ARTIST_MBID_COLUMN = "artist_mbid"
        const val FETCHED_AT_COLUMN = "fetched_at"
    }
}

@Embeddable
class SimilarArtist(
    @field:Column(name = MBID_COLUMN, nullable = false, length = 36)
    var mbid: String,

    @field:Column(name = NAME_COLUMN, nullable = false, length = 255)
    var name: String,

    @field:Column(name = SCORE_COLUMN, nullable = false)
    var score: Int,
) {
    companion object {
        const val TABLE_NAME = "similar_artist"
        const val ARTIST_MBID_COLUMN = "artist_mbid"
        const val MBID_COLUMN = "similar_artist_mbid"
        const val NAME_COLUMN = "name"
        const val SCORE_COLUMN = "score"
    }
}
