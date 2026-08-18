package com.umt.core.media.hardcover

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the default-value constructors directly — the mapper/client tests always pass
 * every field explicitly, which never touches these classes' own default-argument bytecode.
 */
class HardcoverBookResponseTest {

    @Test
    fun `HardcoverGraphQlResponse defaults data to null`() {
        assertEquals(null, HardcoverGraphQlResponse().data)
    }

    @Test
    fun `HardcoverData defaults books to an empty list`() {
        assertTrue(HardcoverData().books.isEmpty())
    }

    @Test
    fun `HardcoverBook defaults contributions to an empty list`() {
        val book = HardcoverBook(
            id = 1L,
            title = "Dune",
            description = null,
            slug = null,
            releaseDate = null,
            image = null,
        )

        assertTrue(book.contributions.isEmpty())
    }
}
