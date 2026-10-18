package com.umt.core.media

import com.umt.api.generated.model.MediaCategory as ApiMediaCategory
import com.umt.api.generated.model.ReleaseStatus as ApiReleaseStatus
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper

@Mapper(componentModel = "spring", config = MapperConfig::class)
interface MediaMapper {
    fun toDomainMediaCategory(mediaCategory: ApiMediaCategory): MediaCategory

    fun toDomainReleaseStatus(releaseStatus: ApiReleaseStatus): ReleaseStatus
}
