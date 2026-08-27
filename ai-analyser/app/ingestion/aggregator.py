import logging

from app.ingestion import google_news_client, rss_client, youtube_client

log = logging.getLogger(__name__)


def fetch_all_posts(media_title: str, media_type: str | None = None) -> list[dict]:
    """
    Fans out to every ingestion source. The module references below are looked up on every
    call (not cached at import time), so tests can patch e.g. app.ingestion.aggregator.rss_client
    and have it take effect. Every source follows the same house rule as the rest of the
    pipeline: one bad source logs and gets skipped, it never aborts the whole run.

    The media_type (MOVIE/TV_SHOW/GAME/BOOK/MUSIC, or None if unknown) is passed to every source
    uniformly - most ignore it, rss_client uses it to skip feeds that don't apply (no point
    asking IGN about a book).
    """
    posts: list[dict] = []

    for source in (google_news_client, rss_client, youtube_client):
        try:
            posts.extend(source.fetch_posts(media_title, media_type=media_type))
        except Exception:
            log.exception(
                "Ingestion source %s failed, skipping it this run",
                getattr(source, "__name__", repr(source)),
            )

    return posts
