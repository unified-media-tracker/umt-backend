package com.umt.core.media

import com.umt.core.media.MediaSpecifications.listing
import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

/**
 * The listing predicates run against a real PostgreSQL - the same reason CoreServiceApplicationTests
 * avoids H2: `release_date_status` is a native enum column, and a filter that quietly hides the
 * wrong rows is precisely the bug a mocked CriteriaBuilder can't catch.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MediaSpecificationsTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    private lateinit var movieRepository: MovieRepository

    private val monthStart = LocalDate.of(2026, 9, 1)

    private fun movie(title: String, releaseDate: LocalDate?, status: ReleaseStatus) {
        movieRepository.save(Movie(title = title, tmdbId = title, releaseDate = releaseDate, releaseDateStatus = status))
    }

    private fun titles(status: ReleaseStatus? = null, from: LocalDate? = null): Set<String> =
        movieRepository.findAll(listing<Movie>(status, from)).map { it.title }.toSet()

    private fun seedCatalogue() {
        movie("Back catalogue", LocalDate.of(1976, 11, 5), ReleaseStatus.RELEASED)
        movie("Last day of August", monthStart.minusDays(1), ReleaseStatus.RELEASED)
        movie("First day of the month", monthStart, ReleaseStatus.RELEASED)
        movie("Out this month", LocalDate.of(2026, 9, 18), ReleaseStatus.RELEASED)
        movie("Next month", LocalDate.of(2026, 10, 9), ReleaseStatus.CONFIRMED)
        movie("Next year", LocalDate.of(2027, 3, 12), ReleaseStatus.ANNOUNCED)
        movie("Date to be announced", null, ReleaseStatus.TBA)
    }

    @Test
    @DisplayName("no filters is no restriction at all")
    fun noFilters() {
        seedCatalogue()

        assertEquals(7, titles().size)
    }

    @Test
    @DisplayName("releasingFrom drops everything before the cutoff and keeps the cutoff day itself")
    fun releasingFrom() {
        seedCatalogue()

        assertEquals(
            setOf("First day of the month", "Out this month", "Next month", "Next year", "Date to be announced"),
            titles(from = monthStart),
        )
    }

    @Test
    @DisplayName("undated items survive the cutoff - they can't be placed before it and are still unreleased")
    fun undatedItemsAreKept() {
        seedCatalogue()

        assertEquals(setOf("Date to be announced"), titles(status = ReleaseStatus.TBA, from = monthStart))
    }

    @Test
    @DisplayName("status filters on its own")
    fun statusOnly() {
        seedCatalogue()

        assertEquals(setOf("Next month"), titles(status = ReleaseStatus.CONFIRMED))
    }

    @Test
    @DisplayName("status and cutoff combine with AND, not OR")
    fun statusAndCutoff() {
        seedCatalogue()

        assertEquals(
            setOf("First day of the month", "Out this month"),
            titles(status = ReleaseStatus.RELEASED, from = monthStart),
        )
    }
}
