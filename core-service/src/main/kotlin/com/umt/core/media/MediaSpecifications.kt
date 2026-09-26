package com.umt.core.media

import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

/**
 * The listing predicates in one place, rather than a derived-query method per filter combination
 * on each of the five repositories - they all share MediaItem's releaseDate/releaseDateStatus.
 */
object MediaSpecifications {

    private const val RELEASE_DATE = "releaseDate"
    private const val RELEASE_DATE_STATUS = "releaseDateStatus"

    fun <T : MediaItem> withStatus(status: ReleaseStatus): Specification<T> =
        Specification { root, _, cb -> cb.equal(root.get<ReleaseStatus>(RELEASE_DATE_STATUS), status) }

    // Undated (TBA) items are kept: they can't be placed before the cutoff, and they're still unreleased.
    fun <T : MediaItem> releasingFrom(from: LocalDate): Specification<T> =
        Specification { root, _, cb ->
            cb.or(
                cb.isNull(root.get<LocalDate>(RELEASE_DATE)),
                cb.greaterThanOrEqualTo(root.get<LocalDate>(RELEASE_DATE), from),
            )
        }

    // Both nulls are "no filter at all" - an always-true specification, not a special case for callers.
    fun <T : MediaItem> listing(status: ReleaseStatus?, from: LocalDate?): Specification<T> =
        listOfNotNull(status?.let { withStatus<T>(it) }, from?.let { releasingFrom<T>(it) })
            .fold(Specification.where<T>(null)) { all, next -> all.and(next) }
}
