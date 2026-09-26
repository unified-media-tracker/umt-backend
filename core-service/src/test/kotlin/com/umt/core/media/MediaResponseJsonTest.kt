package com.umt.core.media

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.umt.api.generated.model.ExternalSourceType
import com.umt.api.generated.model.MediaCategory
import com.umt.api.generated.model.MediaResponse
import com.umt.api.generated.model.MovieResponse
import com.umt.api.generated.model.MusicResponse
import com.umt.api.generated.model.ReleaseStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import java.math.BigDecimal
import java.util.UUID

@JsonTest
class MediaResponseJsonTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val listType = object : TypeReference<List<MediaResponse>>() {}

    private val movie = MovieResponse(
        id = UUID.randomUUID(),
        mediaCategory = MediaCategory.MOVIE,
        title = "Dune: Part Three",
        releaseDateStatus = ReleaseStatus.CONFIRMED,
        popularityScore = BigDecimal.ONE,
        ratingCount = 0,
        externalSource = ExternalSourceType.TMDB,
        externalSourceId = "1",
        runtimeMinutes = 139,
    )

    private val music = MusicResponse(
        id = UUID.randomUUID(),
        mediaCategory = MediaCategory.MUSIC,
        title = "In Rainbows",
        releaseDateStatus = ReleaseStatus.ANNOUNCED,
        popularityScore = BigDecimal.ONE,
        ratingCount = 0,
        externalSource = ExternalSourceType.MUSICBRAINZ,
        externalSourceId = "2",
    )

    private fun serialise(items: List<MediaResponse>): String =
        objectMapper.writerFor(listType).writeValueAsString(items)

    @Test
    fun `mediaCategory is written once per item`() {
        val json = serialise(listOf(movie, music))

        assertEquals(2, Regex("\"mediaCategory\"").findAll(json).count())
    }

    @Test
    fun `an item only carries the fields of its own category`() {
        val items: JsonNode = objectMapper.readTree(serialise(listOf(movie, music)))

        assertTrue(items[0].has("runtimeMinutes"))
        assertFalse(items[1].has("runtimeMinutes"))
    }

    @Test
    fun `the subtype is picked from mediaCategory when reading back`() {
        val back = objectMapper.readValue(serialise(listOf(movie, music)), listType)

        assertEquals(listOf(MovieResponse::class, MusicResponse::class), back.map { it::class })
        assertEquals(139, (back[0] as MovieResponse).runtimeMinutes)
        assertEquals(MediaCategory.MUSIC, back[1].mediaCategory)
    }
}
