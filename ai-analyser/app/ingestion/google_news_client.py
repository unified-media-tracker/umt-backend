import time
import urllib.parse

import feedparser


def fetch_posts(media_title: str, media_type: str | None = None, limit: int = 3):
    """
    Fetches news from Google News RSS based on the query. The media_type is accepted for a
    uniform fetch_posts signature across ingestion sources (aggregator.py calls every source
    the same way) but unused here - Google News is a general aggregator, relevant to every
    media type.
    Returns: [{source_name, source_url, source_reputation_score, text, published_at}, ...]
    """
    # Two things that silently zeroed out real results, confirmed live against Google News:
    # 1. A leading dash in a word (e.g. "-Starting") is Google's exclusion operator, and the
    #    "-Subtitle-" bracketing convention common in anime/light-novel titles (e.g. "Re:ZERO
    #    -Starting Life in Another World-") means the title excludes its own subtitle from the
    #    search. Dashes carry no reliable positive meaning here, so they're stripped outright.
    # 2. Exact-phrase quoting requires an article to reproduce the whole title verbatim - fine
    #    for a short title ("Silksong"), but no real article repeats a long compound title
    #    (subtitle, volume/chapter number and all) word-for-word. Unquoted relevance search
    #    handles both short and long titles without this cliff.
    query = f"{media_title.replace('-', ' ')} release OR delay OR delayed OR launch"

    encoded_query = urllib.parse.quote(query)
    rss_url = f"https://news.google.com/rss/search?q={encoded_query}&hl=en-US&gl=US&ceid=US:en"

    feed = feedparser.parse(rss_url)
    results = []
    
    for entry in feed.entries[:limit]:
        source_name = entry.get('source', {}).get('title', 'Google News')
        
        published_at = time.mktime(entry.published_parsed) if hasattr(entry, 'published_parsed') else time.time()
        
        results.append({
            "source_name": source_name,
            "source_url": entry.link,
            "source_reputation_score": 1.0,
            "text": f"{entry.title}. {entry.get('summary', '')}",
            "published_at": published_at,
        })
    return results
