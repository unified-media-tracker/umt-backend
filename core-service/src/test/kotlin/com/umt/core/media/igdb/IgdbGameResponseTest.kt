package com.umt.core.media.igdb

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the default-value constructors directly — other tests always pass every field
 * explicitly, which never touches these classes' own default-argument bytecode.
 */
class IgdbGameResponseTest {

    @Test
    fun `IgdbGame defaults involvedCompanies to an empty list`() {
        val game = IgdbGame(id = 1, name = "Half-Life 3", firstReleaseDate = null, summary = null, cover = null)

        assertTrue(game.involvedCompanies.isEmpty())
    }

    @Test
    fun `IgdbInvolvedCompany defaults developer and publisher to false`() {
        val involved = IgdbInvolvedCompany(company = IgdbCompany(id = 1, name = "Valve"))

        assertFalse(involved.developer)
        assertFalse(involved.publisher)
    }
}
