package com.umt.core.media.music.metacritic

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class UpcomingMusicCandidate(
    val artist: String,
    val title: String,
    val releaseDate: LocalDate,
)

/**
 * Scrapes Metacritic's "Upcoming Album Release Calendar" for {artist, title, date}
 * only — nothing else on the page (scores, reviews, images) is touched. Everything
 * else about a release comes from MusicBrainzClient once this has told us a title
 * and date exist completely.
 *
 * Metacritic's robots.txt disallows /search but not /browse/, and this hits one
 * public page on a daily schedule, not per-request — see MusicCatalogSyncScheduler.
 */
@Component
class MetacriticMusicClient {

    private val log = LoggerFactory.getLogger(javaClass)

    fun fetchUpcomingMusic(): List<UpcomingMusicCandidate> {
        val document = Jsoup.connect(COMING_SOON_URL)
            .userAgent(USER_AGENT)
            .timeout(10_000)
            .get()

        val table = document.selectFirst("table.musicTable")
        if (table == null) {
            log.error("musicTable not found on {} — Metacritic likely changed its markup", COMING_SOON_URL)
            return emptyList()
        }

        val releases = mutableListOf<UpcomingMusicCandidate>()
        var currentDate: LocalDate? = null

        for (row in table.select("tr")) {
            val header = row.selectFirst("th.head")
            if (header != null) {
                currentDate = parseHeaderDate(header.text())
                continue
            }

            val artist = row.selectFirst("td.artistName")?.text()?.trim()
            val title = row.selectFirst("td.albumTitle")?.text()?.trim()
            val date = currentDate

            if (artist.isNullOrBlank() || title.isNullOrBlank() || date == null) continue
            releases.add(UpcomingMusicCandidate(artist, title, date))
        }

        log.info("Parsed {} upcoming albums from Metacritic", releases.size)
        return releases
    }

    private fun parseHeaderDate(text: String): LocalDate? =
        try {
            LocalDate.parse(text.trim(), DATE_FORMAT)
        } catch (e: DateTimeParseException) {
            log.warn("Unrecognized date header on coming-soon page: '{}'", text)
            null
        }

    companion object {
        private const val COMING_SOON_URL = "https://www.metacritic.com/browse/albums/release-date/coming-soon/date"
        private const val USER_AGENT = "UMT-CatalogSync/0.1"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    }
}
