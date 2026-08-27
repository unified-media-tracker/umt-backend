"""
A single pipeline run isn't a verdict - the trend only means anything once there's enough
history, and it should only call "RISING"/"FALLING" when the movement across runs is both
monotonic and big enough to matter, not just noisy jitter around a flat value.
"""
from datetime import date
from unittest.mock import MagicMock

from app.analysis.confidence_trend import compute_trend, record_delay_probability


def history_row(delay_probability):
    row = MagicMock()
    row.delay_probability = delay_probability
    return row


def session_with_history(rows_newest_first):
    """An SQLAlchemy session whose chained query returns rows_newest_first, matching the
    real query's .order_by(computed_at.desc())."""
    session = MagicMock()
    session.query.return_value.filter.return_value.order_by.return_value.limit.return_value.all.return_value = (
        rows_newest_first
    )
    return session


class TestNotEnoughHistory:
    def test_fewer_than_three_history_rows_returns_none(self):
        session = session_with_history([history_row(50), history_row(40)])

        assert compute_trend(session, "some-id") is None

    def test_no_history_at_all_returns_none(self):
        session = session_with_history([])

        assert compute_trend(session, "some-id") is None


class TestRising:
    def test_a_steady_climb_past_the_threshold_is_rising(self):
        # newest-first from the query; oldest-to-newest is 10 -> 30 -> 45
        session = session_with_history([history_row(45), history_row(30), history_row(10)])

        assert compute_trend(session, "some-id") == "RISING"

    def test_a_small_climb_under_the_threshold_is_not_called_rising(self):
        # oldest-to-newest: 40 -> 42 -> 45, a 5-point move total
        session = session_with_history([history_row(45), history_row(42), history_row(40)])

        assert compute_trend(session, "some-id") == "STABLE"


class TestFalling:
    def test_a_steady_decline_past_the_threshold_is_falling(self):
        # oldest-to-newest: 80 -> 60 -> 40
        session = session_with_history([history_row(40), history_row(60), history_row(80)])

        assert compute_trend(session, "some-id") == "FALLING"


class TestStable:
    def test_flat_values_are_stable(self):
        session = session_with_history([history_row(50), history_row(50), history_row(50)])

        assert compute_trend(session, "some-id") == "STABLE"

    def test_non_monotonic_jitter_is_stable_not_rising_or_falling(self):
        # oldest-to-newest: 50 -> 20 -> 55 - neither purely up nor purely down
        session = session_with_history([history_row(55), history_row(20), history_row(50)])

        assert compute_trend(session, "some-id") == "STABLE"


class TestRecordDelayProbability:
    def test_adds_and_commits_a_history_row(self):
        session = MagicMock()

        record_delay_probability(session, "some-id", delay_probability=42.0, signal_count=7)

        session.add.assert_called_once()
        session.commit.assert_called_once()
        added = session.add.call_args.args[0]
        assert added.media_item_id == "some-id"
        assert added.delay_probability == 42.0
        assert added.signal_count == 7
        assert added.known_release_date is None

    def test_records_the_known_release_date_when_given(self):
        session = MagicMock()

        record_delay_probability(
            session, "some-id", delay_probability=42.0, signal_count=7, known_release_date=date(2026, 11, 19),
        )

        added = session.add.call_args.args[0]
        assert added.known_release_date == date(2026, 11, 19)
