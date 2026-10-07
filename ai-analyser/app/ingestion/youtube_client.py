import logging
from datetime import datetime, timezone

import requests

from app.common.config import settings

log = logging.getLogger(__name__)

SEARCH_URL = "https://www.googleapis.com/youtube/v3/search"

_warned_missing_key = False


def fetch_posts(media_title: str, media_category: str | None = None, limit: int = 5):
    """
    Fetches recent videos mentioning media_title via the YouTube Data API - a lot of
    game/movie leaks and datamining surface here well before text press picks them up.
    The media_category is accepted for a uniform fetch_posts signature across ingestion sources,
    but unused here.
    Returns: [{source_name, source_url, source_reputation_score, text, published_at}, ...]
    """
    global _warned_missing_key
    if not settings.youtube_api_key:
        if not _warned_missing_key:
            log.warning("youtube_api_key is not configured, skipping YouTube ingestion")
            _warned_missing_key = True
        return []

    response = requests.get(
        SEARCH_URL,
        params={
            "part": "snippet",
            "q": f"{media_title} release date OR delay OR delayed",
            "type": "video",
            "order": "date",
            "maxResults": limit,
            "key": settings.youtube_api_key,
        },
        timeout=10,
    )
    response.raise_for_status()
    items = response.json().get("items", [])

    results = []
    for item in items:
        video_id = item.get("id", {}).get("videoId")
        snippet = item.get("snippet", {})
        if not video_id or not snippet:
            continue

        title = snippet.get("title", "")
        description = snippet.get("description", "")
        published_at_raw = snippet.get("publishedAt")

        try:
            published_at = (
                datetime.fromisoformat(published_at_raw.replace("Z", "+00:00")).timestamp()
                if published_at_raw else datetime.now(timezone.utc).timestamp()
            )
        except ValueError:
            published_at = datetime.now(timezone.utc).timestamp()

        results.append({
            "source_name": snippet.get("channelTitle", "YouTube"),
            "source_url": f"https://www.youtube.com/watch?v={video_id}",
            "source_reputation_score": 1.0,
            "text": f"{title}. {description}",
            "published_at": published_at,
        })

    return results
