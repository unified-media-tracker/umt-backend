package com.umt.core.media

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ReleasedStatusSchedulerTest {

    @Test
    fun `the scheduled run triggers the released-status sweep`() {
        val releaseDateSyncService = mockk<ReleaseDateSyncService>(relaxed = true)

        ReleasedStatusScheduler(releaseDateSyncService).checkForNewlyReleasedItems()

        verify(exactly = 1) { releaseDateSyncService.checkForNewlyReleasedItems() }
    }
}
