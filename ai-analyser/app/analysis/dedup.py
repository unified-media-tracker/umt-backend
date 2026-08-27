import logging
import math

import requests

log = logging.getLogger(__name__)

OLLAMA_EMBEDDINGS_URL = "http://localhost:11434/api/embeddings"
EMBEDDING_MODEL = "nomic-embed-text"

DEFAULT_SIMILARITY_THRESHOLD = 0.92


def get_embedding(text: str) -> list[float]:
    response = requests.post(
        OLLAMA_EMBEDDINGS_URL,
        json={"model": EMBEDDING_MODEL, "prompt": text},
        timeout=15,
    )
    response.raise_for_status()
    return response.json()["embedding"]


def _cosine_similarity(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = math.sqrt(sum(x * x for x in a))
    norm_b = math.sqrt(sum(y * y for y in b))
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)


def deduplicate_posts(posts: list[dict], threshold: float = DEFAULT_SIMILARITY_THRESHOLD) -> list[dict]:
    """
    Collapses posts that are near-duplicate re-reports of the same underlying story
    into one representative per cluster, so a repost doesn't inflate the delay probability's
    volume factor or cost an extra LLM call.
    """
    kept: list[tuple[dict, list[float]]] = []
    result: list[dict] = []

    for post in posts:
        try:
            embedding = get_embedding(post["text"])
        except Exception:
            log.exception("Failed to embed post from %s, keeping it un-deduped", post.get("source_url"))
            result.append(post)
            continue

        if any(_cosine_similarity(embedding, kept_embedding) >= threshold for _, kept_embedding in kept):
            continue

        kept.append((post, embedding))
        result.append(post)

    return result
