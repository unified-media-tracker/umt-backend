"""
media_item_id is the primary key on MediaOutcome by design - a release is a one-way, one-time
transition, so the only two things that can happen here are "record it" and "it's already
recorded" (RabbitMQ's at-least-once delivery). The second case must be a quiet no-op, not an
error that gets logged as a real failure.
"""
from datetime import date
from unittest.mock import MagicMock
from uuid import uuid4

from sqlalchemy.exc import IntegrityError

from app.messaging.outcome_consumer import handle_media_released


class TestHandleMediaReleased:
    def test_records_the_outcome_and_commits(self):
        session = MagicMock()
        media_item_id = uuid4()

        handle_media_released(media_item_id, date(2026, 11, 19), session)

        session.add.assert_called_once()
        added = session.add.call_args.args[0]
        assert added.media_item_id == media_item_id
        assert added.actual_release_date == date(2026, 11, 19)
        session.commit.assert_called_once()
        session.rollback.assert_not_called()

    def test_a_duplicate_delivery_is_a_quiet_no_op(self):
        session = MagicMock()
        session.commit.side_effect = IntegrityError("insert", {}, Exception("duplicate key"))

        handle_media_released(uuid4(), date(2026, 11, 19), session)

        session.rollback.assert_called_once()
