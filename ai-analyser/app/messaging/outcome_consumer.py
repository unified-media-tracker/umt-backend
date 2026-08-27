import logging
from datetime import date
from uuid import UUID

from sqlalchemy.exc import IntegrityError

from app.db.models import MediaOutcome

log = logging.getLogger(__name__)


def handle_media_released(media_item_id: UUID, actual_release_date: date, session) -> None:
    """Records that a media item released. A duplicate delivery just hits the primary-key
    conflict below and is skipped, not treated as an error."""
    try:
        session.add(MediaOutcome(media_item_id=media_item_id, actual_release_date=actual_release_date))
        session.commit()
        log.info("Recorded release outcome for %s: %s", media_item_id, actual_release_date)
    except IntegrityError:
        session.rollback()
        log.info("Outcome for %s already recorded, skipping duplicate delivery", media_item_id)
