"""
A single pipeline run is a snapshot, not a verdict - the trend only means anything once
there's enough history, and it should only call "rising"/"falling" when the movement across
runs is both monotonic and big enough to matter, not just noisy jitter around a flat value.
"""
from unittest.mock import MagicMock

from app.analysis.trend import compute_trend, record_snapshot


def snapshot(delay_probability):
    row = MagicMock()
    row.delay_probability = delay_probability
    return row


def session_with_snapshots(rows_newest_first):
    """An SQLAlchemy session whose chained query returns rows_newest_first, matching the
    real query's .order_by(computed_at.desc())."""
    session = MagicMock()
    session.query.return_value.filter.return_value.order_by.return_value.limit.return_value.all.return_value = (
        rows_newest_first
    )
    return session


class TestNotEnoughHistory:
    def test_fewer_than_three_snapshots_returns_none(self):
        session = session_with_snapshots([snapshot(50), snapshot(40)])

        assert compute_trend(session, "some-id") is None

    def test_no_snapshots_at_all_returns_none(self):
        session = session_with_snapshots([])

        assert compute_trend(session, "some-id") is None


class TestRising:
    def test_a_steady_climb_past_the_threshold_is_rising(self):
        # newest-first from the query; oldest-to-newest is 10 -> 30 -> 45
        session = session_with_snapshots([snapshot(45), snapshot(30), snapshot(10)])

        assert compute_trend(session, "some-id") == "rising"

    def test_a_small_climb_under_the_threshold_is_not_called_rising(self):
        # oldest-to-newest: 40 -> 42 -> 45, a 5-point move total
        session = session_with_snapshots([snapshot(45), snapshot(42), snapshot(40)])

        assert compute_trend(session, "some-id") == "stable"


class TestFalling:
    def test_a_steady_decline_past_the_threshold_is_falling(self):
        # oldest-to-newest: 80 -> 60 -> 40
        session = session_with_snapshots([snapshot(40), snapshot(60), snapshot(80)])

        assert compute_trend(session, "some-id") == "falling"


class TestStable:
    def test_flat_values_are_stable(self):
        session = session_with_snapshots([snapshot(50), snapshot(50), snapshot(50)])

        assert compute_trend(session, "some-id") == "stable"

    def test_non_monotonic_jitter_is_stable_not_rising_or_falling(self):
        # oldest-to-newest: 50 -> 20 -> 55 - neither purely up nor purely down
        session = session_with_snapshots([snapshot(55), snapshot(20), snapshot(50)])

        assert compute_trend(session, "some-id") == "stable"


class TestRecordSnapshot:
    def test_adds_and_commits_a_snapshot(self):
        session = MagicMock()

        record_snapshot(session, "some-id", delay_probability=42.0, signal_count=7)

        session.add.assert_called_once()
        session.commit.assert_called_once()
        added = session.add.call_args.args[0]
        assert added.media_item_id == "some-id"
        assert added.delay_probability == 42.0
        assert added.signal_count == 7
