"""
Source reputation is a cache in front of the LLM: known sources come from the database, unknown
ones cost one model call and are then persisted. Two things matter here — that a cache hit never
reaches the model, and that an LLM failure degrades to a neutral score instead of killing the
whole pipeline run.
"""
from unittest.mock import MagicMock, patch

from app.analysis.source_reputation import get_source_reputation
from app.schemas import SourceReputationEvaluation


def session_returning(existing):
    """An SQLAlchemy session whose query(...).filter(...).first() yields `existing`."""
    session = MagicMock()
    session.query.return_value.filter.return_value.first.return_value = existing
    return session


def stored_row(score, name="eurogamer"):
    row = MagicMock()
    row.reputation_score = score
    row.source_name = name
    return row


class TestCacheHit:
    def test_known_source_is_read_from_the_database(self):
        session = session_returning(stored_row(0.85))

        with patch("app.analysis.source_reputation.ask_ollama") as ask:
            score = get_source_reputation("Eurogamer", session)

        assert score == 0.85
        ask.assert_not_called()

    def test_cached_score_is_returned_as_a_float(self):
        """The column is Numeric, so SQLAlchemy hands back Decimal — downstream maths needs float."""
        from decimal import Decimal

        session = session_returning(stored_row(Decimal("0.75")))

        with patch("app.analysis.source_reputation.ask_ollama"):
            score = get_source_reputation("Eurogamer", session)

        assert isinstance(score, float)
        assert score == 0.75

    def test_lookup_is_case_and_whitespace_insensitive(self):
        session = session_returning(stored_row(0.9))

        with patch("app.analysis.source_reputation.ask_ollama"):
            get_source_reputation("  EuroGamer  ", session)

        # the normalised form is what gets compared, not the raw source name
        session.query.return_value.filter.assert_called_once()


class TestCacheMiss:
    def test_unknown_source_is_scored_by_the_model_and_persisted(self):
        session = session_returning(None)
        evaluation = SourceReputationEvaluation(
            reasoning="Industry-leading outlet.", reputation_score=0.95
        )

        with patch(
            "app.analysis.source_reputation.ask_ollama", return_value=(evaluation, "{}")
        ) as ask:
            score = get_source_reputation("Bloomberg", session)

        assert score == 0.95
        ask.assert_called_once()
        session.add.assert_called_once()
        session.commit.assert_called_once()

        persisted = session.add.call_args.args[0]
        assert persisted.source_name == "bloomberg"
        assert persisted.reputation_score == 0.95
        assert persisted.is_curated is False
        assert persisted.reasoning == "Industry-leading outlet."

    def test_source_name_is_normalised_before_being_stored(self):
        session = session_returning(None)
        evaluation = SourceReputationEvaluation(reasoning="r", reputation_score=0.4)

        with patch("app.analysis.source_reputation.ask_ollama", return_value=(evaluation, "{}")):
            get_source_reputation("  IGN  ", session)

        assert session.add.call_args.args[0].source_name == "ign"


class TestLlmFailure:
    def test_falls_back_to_a_neutral_score_when_the_model_fails(self):
        session = session_returning(None)

        with patch(
            "app.analysis.source_reputation.ask_ollama",
            side_effect=RuntimeError("ollama is down"),
        ):
            score = get_source_reputation("Some Random Blog", session)

        assert score == 0.4

    def test_nothing_is_persisted_when_the_model_fails(self):
        session = session_returning(None)

        with patch(
            "app.analysis.source_reputation.ask_ollama", side_effect=ValueError("bad json")
        ):
            get_source_reputation("Some Random Blog", session)

        session.add.assert_not_called()
        session.commit.assert_not_called()

    def test_failure_does_not_propagate_to_the_caller(self):
        """A single unscorable source must not abort the whole pipeline run."""
        session = session_returning(None)

        with patch(
            "app.analysis.source_reputation.ask_ollama", side_effect=Exception("anything")
        ):
            assert get_source_reputation("4chan", session) == 0.4
