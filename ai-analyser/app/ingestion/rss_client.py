import logging
import time

import feedparser

log = logging.getLogger(__name__)

# Specialist trade press, not general news aggregators. Each entry is (display name used as
# source_name, feed URL, the media types it's relevant to) - verified, reachable and
# parseable manually before being hardcoded here. Media type strings match core-service's
# MediaType enum (MOVIE, TV_SHOW, GAME, BOOK, MUSIC) as it comes over the wire in the
# media.imported event - no point asking IGN about a book.
CURATED_FEEDS = [
    ("IGN", "https://feeds.ign.com/ign/all", {"GAME"}),
    ("Eurogamer", "https://www.eurogamer.net/feed", {"GAME"}),
    ("GameSpot", "https://www.gamespot.com/feeds/game-news/", {"GAME"}),
    ("Variety", "https://variety.com/feed/", {"MOVIE", "TV_SHOW"}),
    ("Deadline", "https://deadline.com/feed/", {"MOVIE", "TV_SHOW"}),
    ("The Hollywood Reporter", "https://www.hollywoodreporter.com/feed/", {"MOVIE", "TV_SHOW"}),
    ("Literary Hub", "https://lithub.com/feed/", {"BOOK"}),
    ("Book Riot", "https://bookriot.com/feed/", {"BOOK"}),
    ("Pitchfork", "https://pitchfork.com/feed/feed-news/rss", {"MUSIC"}),
    ("Billboard", "https://www.billboard.com/feed/", {"MUSIC"}),
    ("Rolling Stone Music", "https://www.rollingstone.com/music/music-news/feed/", {"MUSIC"}),
]


def fetch_posts(media_title: str, media_type: str | None = None, limit_per_feed: int = 5):
    """
    Fetches recent articles from a fixed list of curated gaming/movie trade press RSS feeds,
    keeping only entries that mention media_title in their title or summary.
    Returns: [{source_name, source_url, source_reputation_score, text, published_at}, ...]
    """
    needle = media_title.lower()
    results = []

    for source_name, feed_url, applicable_types in CURATED_FEEDS:
        if media_type is not None and media_type not in applicable_types:
            continue

        try:
            feed = feedparser.parse(feed_url)

            matched = 0
            for entry in feed.entries:
                title = entry.get("title", "")
                summary = entry.get("summary", "")
                if needle not in title.lower() and needle not in summary.lower():
                    continue

                published_at = (
                    time.mktime(entry.published_parsed) if hasattr(entry, "published_parsed") else time.time()
                )

                results.append({
                    "source_name": source_name,
                    "source_url": entry.link,
                    "source_reputation_score": 1.0,
                    "text": f"{title}. {summary}",
                    "published_at": published_at,
                })

                matched += 1
                if matched >= limit_per_feed:
                    break
        except Exception:
            log.exception("Failed to fetch/parse curated feed %s (%s), skipping it", source_name, feed_url)

    return results
