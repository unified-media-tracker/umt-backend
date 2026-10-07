package com.umt.core.media

import com.umt.api.generated.model.MediaResponse
import com.umt.api.generated.model.MediaSortOption
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import java.time.LocalDate
import java.util.UUID

/**
 * Cross-type only now - browsing/searching across whichever of the five tables mediaCategory
 * points at. Per-type admin import/sync lives in each type's own Controller/Service instead.
 */
interface MediaService {

    // releaseDateFrom keeps items releasing on or after it, plus undated (TBA) ones - see MediaSpecifications
    fun listMedia(
        mediaCategory: ApiMediaCategory,
        status: ApiReleaseStatus?,
        sort: MediaSortOption?,
        releaseDateFrom: LocalDate? = null,
    ): List<MediaResponse>

    // mediaCategory is an optional fast path - skips straight to that one table instead of trying
    // all five in turn when the caller already knows it
    fun getMediaById(id: UUID, mediaCategory: ApiMediaCategory? = null): MediaResponse
}
