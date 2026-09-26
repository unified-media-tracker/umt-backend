package com.umt.core.media

import com.umt.api.generated.model.ExternalSourceType as ApiExternalSourceType
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.TrendDirection as ApiTrendDirection
import com.umt.core.media.book.Book
import com.umt.core.media.book.BookMapperImpl
import com.umt.core.media.game.Game
import com.umt.core.media.game.GameMapperImpl
import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieMapperImpl
import com.umt.core.media.music.Music
import com.umt.core.media.music.MusicMapperImpl
import com.umt.core.media.tvshow.TvShow
import com.umt.core.media.tvshow.TvShowMapperImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class MediaResponseMappingTest {

    private val extras = ResponseExtras(emptyList(), BigDecimal.valueOf(9), ApiTrendDirection.FALLING)
    private val id = UUID.randomUUID()

    @Test
    fun `a movie maps to a MovieResponse with its runtime and TMDB as the source`() {
        val response = MovieMapperImpl().toResponse(Movie(id = id, title = "Inception", tmdbId = "27205", runtimeMinutes = 148), extras)

        assertEquals(ApiMediaCategory.MOVIE, response.mediaCategory)
        assertEquals(ApiExternalSourceType.TMDB, response.externalSource)
        assertEquals("27205", response.externalSourceId)
        assertEquals(148, response.runtimeMinutes)
        assertEquals(BigDecimal.valueOf(9), response.latestDelayProbability)
        assertEquals(ApiTrendDirection.FALLING, response.latestConfidenceTrend)
    }

    @Test
    fun `a tv show maps with TMDB as the source`() {
        val response = TvShowMapperImpl().toResponse(TvShow(id = id, title = "Severance", tmdbId = "95396"), extras)

        assertEquals(ApiMediaCategory.TV_SHOW, response.mediaCategory)
        assertEquals(ApiExternalSourceType.TMDB, response.externalSource)
        assertEquals("95396", response.externalSourceId)
        assertEquals(BigDecimal.valueOf(9), response.latestDelayProbability)
    }

    @Test
    fun `a game maps with IGDB as the source`() {
        val response = GameMapperImpl().toResponse(Game(id = id, title = "Half-Life 3", igdbId = "1942"), extras)

        assertEquals(ApiMediaCategory.GAME, response.mediaCategory)
        assertEquals(ApiExternalSourceType.IGDB, response.externalSource)
        assertEquals("1942", response.externalSourceId)
        assertEquals(BigDecimal.valueOf(9), response.latestDelayProbability)
    }

    @Test
    fun `a book maps with Hardcover as the source`() {
        val response = BookMapperImpl().toResponse(Book(id = id, title = "Project Hail Mary", hardcoverId = "235"), extras)

        assertEquals(ApiMediaCategory.BOOK, response.mediaCategory)
        assertEquals(ApiExternalSourceType.HARDCOVER, response.externalSource)
        assertEquals("235", response.externalSourceId)
        assertEquals(BigDecimal.valueOf(9), response.latestDelayProbability)
    }

    @Test
    fun `a music release maps with MusicBrainz as the source`() {
        val response = MusicMapperImpl().toResponse(Music(id = id, title = "In Rainbows", musicbrainzId = "mb-1"), extras)

        assertEquals(ApiMediaCategory.MUSIC, response.mediaCategory)
        assertEquals(ApiExternalSourceType.MUSICBRAINZ, response.externalSource)
        assertEquals("mb-1", response.externalSourceId)
        assertEquals(BigDecimal.valueOf(9), response.latestDelayProbability)
    }
}
