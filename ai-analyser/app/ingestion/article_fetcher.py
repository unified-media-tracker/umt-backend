import logging

import requests
import trafilatura

log = logging.getLogger(__name__)

# Below this, the extraction is considered too thin to be worth trusting over the fallback
# (e.g. a paywall stub, or a JS-rendered shell trafilatura couldn't parse)
MIN_EXTRACTED_LENGTH = 200

# YouTube watch pages are JS-rendered - the static HTML requests.get() sees has no video
# description at all, only nav/footer chrome
NON_ARTICLE_DOMAINS = ("youtube.com", "youtu.be")


def fetch_full_text(url: str, fallback: str, timeout: int = 8) -> str:
    """
    Downloads the linked page and extracts its main article text. Falls back to the
    RSS/API snippet already on hand for anything that isn't a real, sufficiently long
    article - a failed request, a timeout, thin/failed extraction, or a known non-article
    domain (video pages).
    """
    if any(domain in url for domain in NON_ARTICLE_DOMAINS):
        return fallback

    try:
        response = requests.get(url, timeout=timeout, headers={"User-Agent": "Mozilla/5.0"})
        response.raise_for_status()
    except requests.exceptions.RequestException:
        log.info("Could not fetch full article for %s, using the existing snippet", url)
        return fallback

    try:
        extracted = trafilatura.extract(response.text, url=url)
    except Exception:
        log.exception("trafilatura failed to extract %s, using the existing snippet", url)
        return fallback

    if not extracted or len(extracted) < MIN_EXTRACTED_LENGTH:
        return fallback

    return extracted
