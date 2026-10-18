package com.umt.core.contribution

import com.umt.api.generated.model.ContributorResponse
import com.umt.shared.config.MapperConfig
import org.mapstruct.Mapper
import org.mapstruct.Mapping

/** Shared across every media type - a credit maps to a ContributorResponse the same way
 * regardless of whether it's crediting a movie, a game, a book or an album. */
@Mapper(componentModel = "spring", config = MapperConfig::class)
interface ContributorMapper {

    @Mapping(target = "id", source = "contributor.id")
    @Mapping(target = "name", source = "contributor.name")
    @Mapping(target = "contributorType", source = "contributor.contributorType")
    fun toContributorResponse(credit: Credit): ContributorResponse
}
