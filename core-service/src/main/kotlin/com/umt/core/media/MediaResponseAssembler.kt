package com.umt.core.media

import com.umt.api.generated.model.MediaItemResponse
import com.umt.core.contribution.CreditRepository
import org.springframework.stereotype.Component

/**
 * MediaItemResponse is an immutable Kotlin data class built by MapStruct straight from
 * MediaItem's own fields — but MediaItem has no "credits" field to map from (Credit only
 * points at MediaItem, not the other way round), so MapStruct can't fill in `contributors`
 * on its own. This does it as a second step: base mapping, then an explicit CreditRepository
 * query, then `.copy()` the result in — sidestepping the lazy-collection timing issues that
 * bit genres twice already (see CreditRepository's findByMediaItemId doc).
 */
@Component
class MediaResponseAssembler(
    private val creditRepository: CreditRepository,
    private val mediaMapper: MediaMapper,
) {
    fun assemble(mediaItem: MediaItem): MediaItemResponse {
        val contributors = creditRepository.findByMediaItemId(mediaItem.id!!)
            .map { mediaMapper.toContributorResponse(it) }

        return mediaMapper.toResponse(mediaItem).copy(contributors = contributors)
    }

    fun assembleList(mediaItems: List<MediaItem>): List<MediaItemResponse> = mediaItems.map(::assemble)
}
