package com.umt.core.media

import com.umt.core.contribution.Contributor
import com.umt.core.contribution.ContributorRepository
import com.umt.core.contribution.ContributorType
import com.umt.core.contribution.Credit
import com.umt.core.contribution.CreditRepository
import com.umt.core.contribution.RoleType
import org.springframework.stereotype.Component

/**
 * Shared find-or-create for anyone/anything credited on a media item — director, writer,
 * artist, author, game studio. The DB shape is identical regardless of source or role, only
 * the id/name extraction upstream differs. Its own bean (not a MediaServiceImpl private method)
 * so TmdbCatalogImporter can call it too without a circular dependency back into MediaServiceImpl.
 */
@Component
class ContributorCreditService(
    private val contributorRepository: ContributorRepository,
    private val creditRepository: CreditRepository,
) {
    fun credit(
        mediaItem: MediaItem,
        source: ExternalSourceType,
        externalId: String,
        name: String,
        role: RoleType,
        contributorType: ContributorType = ContributorType.PERSON,
    ) {
        val contributor = contributorRepository.findByExternalSourceAndExternalSourceId(source, externalId)
            ?: contributorRepository.save(
                Contributor(
                    contributorType = contributorType,
                    name = name,
                    externalSource = source,
                    externalSourceId = externalId,
                )
            )

        creditRepository.save(Credit(mediaItem = mediaItem, contributor = contributor, role = role))
    }
}
