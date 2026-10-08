package com.umt.core.media

import com.umt.api.generated.model.MediaItemResponse
import com.umt.api.generated.model.MediaSortOption
import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import java.util.UUID

/**
 * Cross-type only now - browsing/searching across whichever of the five tables mediaCategory
 * points at. Per-type admin import/sync lives in each type's own Controller/Service instead.
 */
interface MediaService {

    fun listMedia(mediaCategory: ApiMediaCategory, status: ApiReleaseStatus?, sort: MediaSortOption?): List<MediaItemResponse>

    // mediaCategory is an optional fast path - skips straight to that one table instead of trying
    // all five in turn when the caller already knows it
    fun getMediaById(id: UUID, mediaCategory: ApiMediaCategory? = null): MediaItemResponse

    fun getUserRecommendations(userId: Long): List<MediaItemResponse>
}
