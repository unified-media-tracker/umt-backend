import sys
import uuid
from datetime import date

from app.common.config import settings
from app.common.logging_config import configure_logging

configure_logging(settings.log_level)

from app.pipeline import run_pipeline_for_media_item

VALID_MEDIA_CATEGORIES = {"MOVIE", "TV_SHOW", "GAME", "BOOK", "MUSIC"}


def _looks_like_date(value: str) -> bool:
    try:
        date.fromisoformat(value)
        return True
    except ValueError:
        return False


# Usage: python3 run_query.py "Some Title" [MEDIA_CATEGORY] [YYYY-MM-DD]
# Both trailing args are optional, and order-independent - shape recognizes each.
if __name__ == "__main__":
    args = sys.argv[1:]
    media_category = None
    known_release_date = None

    while args and (args[-1].upper() in VALID_MEDIA_CATEGORIES or _looks_like_date(args[-1])):
        candidate = args.pop()
        if candidate.upper() in VALID_MEDIA_CATEGORIES:
            media_category = candidate.upper()
        else:
            known_release_date = candidate

    title = " ".join(args) or "Grand Theft Auto VI"
    run_pipeline_for_media_item(
        uuid.uuid4(), title, media_category=media_category, known_release_date=known_release_date, publish=False,
    )
