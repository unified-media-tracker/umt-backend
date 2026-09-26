package com.umt.core.media.music.similarity

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ArtistSimilarityRepository : JpaRepository<ArtistSimilarity, String> {
    @EntityGraph(attributePaths = ["similarArtists"])
    override fun findById(id: String): Optional<ArtistSimilarity>
}
