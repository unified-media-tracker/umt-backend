package com.umt.core.media.hardcover

import com.umt.core.media.MediaService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class BookCatalogSyncSchedulerTest {

    @Test
    fun `the scheduled run triggers a books sync`() {
        val mediaService = mockk<MediaService>()
        every { mediaService.syncUpcomingBooks() } returns emptyList()

        BookCatalogSyncScheduler(mediaService).syncUpcomingBooks()

        verify(exactly = 1) { mediaService.syncUpcomingBooks() }
    }
}
