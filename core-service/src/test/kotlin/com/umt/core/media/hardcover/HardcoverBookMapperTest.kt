package com.umt.core.media.hardcover

import com.umt.core.media.ExternalSourceType
import com.umt.core.media.MediaType
import com.umt.core.media.ReleaseStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HardcoverBookMapperTest {

    private fun book(
        id: Long = 1L,
        title: String = "Dune",
        description: String? = "A desert planet",
        releaseDate: String? = "2027-01-01",
        image: HardcoverImage? = HardcoverImage("https://example.com/cover.jpg"),
        contributions: List<HardcoverContribution> = emptyList(),
    ) = HardcoverBook(
        id = id,
        title = title,
        description = description,
        slug = "dune",
        releaseDate = releaseDate,
        image = image,
        contributions = contributions,
    )

    @Nested
    @DisplayName("parsedReleaseDate")
    inner class ParsedReleaseDate {

        @Test
        fun `parses a valid ISO date`() {
            assertEquals(LocalDate.of(2027, 1, 1), book(releaseDate = "2027-01-01").parsedReleaseDate)
        }

        @Test
        fun `a null date is null`() {
            assertNull(book(releaseDate = null).parsedReleaseDate)
        }

        @Test
        fun `a blank date is null`() {
            assertNull(book(releaseDate = "  ").parsedReleaseDate)
        }

        @Test
        fun `an unparsable date does not throw, just returns null`() {
            assertNull(book(releaseDate = "not-a-date").parsedReleaseDate)
        }
    }

    @Nested
    @DisplayName("primaryAuthor")
    inner class PrimaryAuthor {

        @Test
        fun `picks the contributor whose contribution is null`() {
            val primary = HardcoverAuthor(1, "Frank Herbert")
            val illustrator = HardcoverAuthor(2, "Someone Else")
            val contributions = listOf(
                HardcoverContribution(author = illustrator, contribution = "Illustrator"),
                HardcoverContribution(author = primary, contribution = null),
            )

            assertEquals(primary, book(contributions = contributions).primaryAuthor)
        }

        @Test
        fun `falls back to the first contributor when none is marked primary`() {
            val first = HardcoverAuthor(1, "Frank Herbert")
            val second = HardcoverAuthor(2, "Someone Else")
            val contributions = listOf(
                HardcoverContribution(author = first, contribution = "Translator"),
                HardcoverContribution(author = second, contribution = "Illustrator"),
            )

            assertEquals(first, book(contributions = contributions).primaryAuthor)
        }

        @Test
        fun `no contributions means no author`() {
            assertNull(book(contributions = emptyList()).primaryAuthor)
        }
    }

    @Nested
    @DisplayName("toMediaItem")
    inner class ToMediaItem {

        @Test
        fun `maps a future release to ANNOUNCED`() {
            val item = book(releaseDate = LocalDate.now().plusDays(30).toString()).toMediaItem()

            assertEquals(MediaType.BOOK, item.mediaType)
            assertEquals("Dune", item.title)
            assertEquals("A desert planet", item.description)
            assertEquals("https://example.com/cover.jpg", item.coverImageUrl)
            assertEquals(ReleaseStatus.ANNOUNCED, item.releaseDateStatus)
            assertEquals(ExternalSourceType.HARDCOVER, item.externalSource)
            assertEquals("1", item.externalSourceId)
        }

        @Test
        fun `maps a past release to RELEASED`() {
            val item = book(releaseDate = LocalDate.now().minusDays(1).toString()).toMediaItem()

            assertEquals(ReleaseStatus.RELEASED, item.releaseDateStatus)
        }

        @Test
        fun `a missing release date is treated as RELEASED`() {
            val item = book(releaseDate = null).toMediaItem()

            assertNull(item.releaseDate)
            assertEquals(ReleaseStatus.RELEASED, item.releaseDateStatus)
        }

        @Test
        fun `a missing cover image maps to a null coverImageUrl`() {
            val item = book(image = null).toMediaItem()

            assertNull(item.coverImageUrl)
        }
    }
}
