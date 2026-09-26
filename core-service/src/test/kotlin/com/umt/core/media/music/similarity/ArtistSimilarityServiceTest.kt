package com.umt.core.media.music.similarity

import com.umt.core.media.music.listenbrainz.ListenBrainzClient
import com.umt.core.media.music.listenbrainz.ListenBrainzSimilarArtist
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import java.time.Duration
import java.time.Instant
import java.util.Optional

class ArtistSimilarityServiceTest {

    private lateinit var client: ListenBrainzClient
    private lateinit var repository: ArtistSimilarityRepository
    private lateinit var service: ArtistSimilarityService

    private val artist = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
    private val now = Instant.parse("2026-09-26T12:00:00Z")
    private val saved = slot<ArtistSimilarity>()

    @BeforeEach
    fun setUp() {
        client = mockk()
        repository = mockk()
        service = ArtistSimilarityService(client, repository)
        every { repository.findById(artist) } returns Optional.empty()
        every { repository.save(capture(saved)) } answers { firstArg() }
    }

    private fun lbArtist(mbid: String, score: Int) = ListenBrainzSimilarArtist(mbid, "Artist $mbid", score)

    private fun stored(fetchedAt: Instant, vararg similar: Pair<String, Int>) =
        ArtistSimilarity(artist, fetchedAt).apply {
            similarArtists = similar.map { (mbid, score) -> SimilarArtist(mbid, "Artist $mbid", score) }.toMutableList()
        }

    @Test
    fun `an artist never looked up is fetched, stored and returned best match first`() {
        every { client.fetchSimilarArtists(artist) } returns listOf(lbArtist("b", 5), lbArtist("a", 9), lbArtist("c", 7))

        val mbids = service.similarArtistMbids(artist, now)

        assertEquals(listOf("a", "c", "b"), mbids)
        assertEquals(now, saved.captured.fetchedAt)
        assertEquals(listOf("a", "c", "b"), saved.captured.similarArtists.map { it.mbid })
    }

    @Test
    fun `a fresh stored lookup is served from the DB without asking ListenBrainz`() {
        every { repository.findById(artist) } returns Optional.of(
            stored(now.minus(ArtistSimilarityService.REFRESH_AFTER).plusSeconds(60), "b" to 5, "a" to 9),
        )

        val mbids = service.similarArtistMbids(artist, now)

        assertEquals(listOf("a", "b"), mbids)
        verify(exactly = 0) { client.fetchSimilarArtists(any()) }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `a lookup older than the refresh window is fetched again and replaced`() {
        every { repository.findById(artist) } returns Optional.of(
            stored(now.minus(ArtistSimilarityService.REFRESH_AFTER).minusSeconds(60), "old" to 1),
        )
        every { client.fetchSimilarArtists(artist) } returns listOf(lbArtist("new", 3))

        val mbids = service.similarArtistMbids(artist, now)

        assertEquals(listOf("new"), mbids)
        assertEquals(now, saved.captured.fetchedAt)
        assertEquals(listOf("new"), saved.captured.similarArtists.map { it.mbid })
    }

    @Test
    fun `an artist ListenBrainz has nothing for is stored empty, so the next request does not ask again`() {
        every { client.fetchSimilarArtists(artist) } returns emptyList()

        val mbids = service.similarArtistMbids(artist, now)

        assertTrue(mbids.isEmpty())
        assertEquals(now, saved.captured.fetchedAt)
        assertTrue(saved.captured.similarArtists.isEmpty())
    }

    @Test
    fun `the artist itself and repeated artists are left out`() {
        every { client.fetchSimilarArtists(artist) } returns listOf(lbArtist(artist, 99), lbArtist("a", 9), lbArtist("a", 8), lbArtist("b", 7))

        val mbids = service.similarArtistMbids(artist, now)

        assertEquals(listOf("a", "b"), mbids)
        assertEquals(listOf("a", "b"), saved.captured.similarArtists.map { it.mbid })
    }

    @Test
    fun `a failed fetch for an artist never looked up returns nothing and stores nothing`() {
        every { client.fetchSimilarArtists(artist) } throws ResourceAccessException("timed out")

        val mbids = service.similarArtistMbids(artist, now)

        assertTrue(mbids.isEmpty())
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `a rejected fetch is treated like any other failed fetch`() {
        every { client.fetchSimilarArtists(artist) } throws HttpClientErrorException(HttpStatus.BAD_REQUEST)

        assertTrue(service.similarArtistMbids(artist, now).isEmpty())
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `a failed fetch for a stale lookup falls back to the stored list and leaves it alone`() {
        every { repository.findById(artist) } returns Optional.of(
            stored(now.minus(ArtistSimilarityService.REFRESH_AFTER).minus(Duration.ofDays(5)), "b" to 5, "a" to 9),
        )
        every { client.fetchSimilarArtists(artist) } throws ResourceAccessException("timed out")

        val mbids = service.similarArtistMbids(artist, now)

        assertEquals(listOf("a", "b"), mbids)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `a failed save does not fail the request`() {
        every { client.fetchSimilarArtists(artist) } returns listOf(lbArtist("a", 9))
        every { repository.save(any()) } throws DataIntegrityViolationException("duplicate key")

        assertEquals(listOf("a"), service.similarArtistMbids(artist, now))
    }
}
