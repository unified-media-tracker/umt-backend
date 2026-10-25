package com.umt.core.media

import com.umt.core.media.book.Book
import com.umt.core.media.book.BookRepository
import com.umt.core.media.game.Game
import com.umt.core.media.game.GameRepository
import com.umt.core.media.music.Music
import com.umt.core.media.music.MusicRepository
import com.umt.core.media.movie.Movie
import com.umt.core.media.movie.MovieRepository
import com.umt.core.media.tvshow.TvShow
import com.umt.core.media.tvshow.TvShowRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

/**
 * One overload per type rather than a single `updateIfChanged(existing: MediaItem, ...)` with
 * runtime dispatch - every real call site already knows its concrete type (it's always called
 * from that type's own importer), so overloading lets each one call its own repository directly
 * and lets the compiler catch a missing type, instead of an `else -> error(...)` found at runtime.
 * mutateIfChanged/recordHistory below holds the one copy of logic that's genuinely the same for
 * all five.
 */
@Component
class ReleaseDateSyncService(
    private val movieRepository: MovieRepository,
    private val tvShowRepository: TvShowRepository,
    private val gameRepository: GameRepository,
    private val bookRepository: BookRepository,
    private val musicRepository: MusicRepository,
    private val releaseStatusHistoryRepository: ReleaseStatusHistoryRepository,
    private val mediaEventPublisher: MediaEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun updateIfChanged(existing: Movie, incomingDate: LocalDate?, sourceLabel: String): Movie =
        applyIfChanged(existing, incomingDate, sourceLabel, movieRepository::save)

    fun updateIfChanged(existing: TvShow, incomingDate: LocalDate?, sourceLabel: String): TvShow =
        applyIfChanged(existing, incomingDate, sourceLabel, tvShowRepository::save)

    fun updateIfChanged(existing: Game, incomingDate: LocalDate?, sourceLabel: String): Game =
        applyIfChanged(existing, incomingDate, sourceLabel, gameRepository::save)

    fun updateIfChanged(existing: Book, incomingDate: LocalDate?, sourceLabel: String): Book =
        applyIfChanged(existing, incomingDate, sourceLabel, bookRepository::save)

    fun updateIfChanged(existing: Music, incomingDate: LocalDate?, sourceLabel: String): Music =
        applyIfChanged(existing, incomingDate, sourceLabel, musicRepository::save)

    private fun <T : MediaItem> applyIfChanged(existing: T, incomingDate: LocalDate?, sourceLabel: String, save: (T) -> T): T {
        if (incomingDate == null) return existing
        val previousDate = existing.releaseDate
        if (previousDate != null && previousDate.isEqual(incomingDate)) return existing

        existing.releaseDate = incomingDate
        if (existing.releaseDateStatus != ReleaseStatus.RELEASED && previousDate != null && incomingDate.isAfter(previousDate)) {
            existing.releaseDateStatus = ReleaseStatus.DELAYED
        }

        val saved = save(existing)
        val note = "$sourceLabel: release date moved from ${previousDate ?: "unset"} to $incomingDate"
        log.info("Release date change detected for '{}': {}", saved.title, note)
        recordHistory(saved, note)
        return saved
    }

    // Daily sweep: marks RELEASED anything whose release date has passed. Separate from
    // updateIfChanged because the "upcoming" sync loops stop seeing an item once upstream
    // drops it from their own "upcoming" feed - right when it ships, right when this matters.
    fun checkForNewlyReleasedItems(today: LocalDate = LocalDate.now()) {
        sweep(movieRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(today, ReleaseStatus.RELEASED), movieRepository::save)
        sweep(tvShowRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(today, ReleaseStatus.RELEASED), tvShowRepository::save)
        sweep(gameRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(today, ReleaseStatus.RELEASED), gameRepository::save)
        sweep(bookRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(today, ReleaseStatus.RELEASED), bookRepository::save)
        sweep(musicRepository.findByReleaseDateLessThanEqualAndReleaseDateStatusNot(today, ReleaseStatus.RELEASED), musicRepository::save)
    }

    private fun <T : MediaItem> sweep(newlyReleased: List<T>, save: (T) -> T) {
        for (item in newlyReleased) {
            item.releaseDateStatus = ReleaseStatus.RELEASED
            val saved = save(item)
            val note = "Release date ${saved.releaseDate} reached, marking as released"
            log.info("'{}' {}", saved.title, note)
            recordHistory(saved, note)
            mediaEventPublisher.publishReleased(saved)
        }
    }

    private fun recordHistory(item: MediaItem, note: String) {
        releaseStatusHistoryRepository.save(
            ReleaseStatusHistory(
                mediaItemId = item.id!!,
                mediaCategory = item.mediaCategory,
                status = item.releaseDateStatus,
                changedAt = Instant.now(),
                sourceNote = note,
            )
        )
    }
}
