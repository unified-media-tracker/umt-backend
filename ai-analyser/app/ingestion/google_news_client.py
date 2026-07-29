import time
import urllib.parse

import feedparser


def fetch_posts(media_title: str, limit: int = 5):
    """
    Fetches news from Google News RSS based on the query.
    Returns: [{source_name, source_url, source_reputation_score, text, published_at}, ...]
    """
    smart_query = f'"{media_title}" release OR delay OR delayed OR launch'

    encoded_query = urllib.parse.quote(smart_query)
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
