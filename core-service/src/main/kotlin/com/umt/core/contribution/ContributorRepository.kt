package com.umt.core.contribution

import com.umt.core.media.ExternalSourceType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContributorRepository : JpaRepository<Contributor, UUID> {
    fun findByExternalSourceAndExternalSourceId(
        externalSource: ExternalSourceType,
        externalSourceId: String,
    ): Contributor?
}
