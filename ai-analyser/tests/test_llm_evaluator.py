"""
The LLM is the least trustworthy component in the service: it is a local 7B model asked to
return structured JSON. These tests pin the contract at the boundary — a bad response has to
surface as a typed error, never as a half-parsed object that reaches the database.
"""
from datetime import date, timedelta
from unittest.mock import MagicMock, patch

import pytest
import requests
from pydantic import BaseModel

from app.analysis.llm_evaluator import MODEL, ask_ollama, evaluate_post
from app.schemas import MAX_PLAUSIBLE_FUTURE, MAX_PLAUSIBLE_PAST, InformationType, PostEvaluation


class Tiny(BaseModel):
    value: int


VALID_EVALUATION = """{
    "reasoning": "The article says the studio pushed the date to Q3.",
    "sentiment_score": -0.8,
    "mentions_delay": true,
    "info_type": "rumor",
    "confidence": 0.9
}"""


def ollama_response(payload: str):
    response = MagicMock()
    response.json.return_value = {"response": payload}
    response.raise_for_status.return_value = None
    return response


class TestAskOllama:
    @patch("app.analysis.llm_evaluator.requests.post")
    def test_parses_a_valid_response_into_the_requested_schema(self, post):
        post.return_value = ollama_response('{"value": 7}')

        parsed, raw = ask_ollama("system", "user", Tiny)

        assert parsed.value == 7
        assert raw == '{"value": 7}'

    @patch("app.analysis.llm_evaluator.requests.post")
    def test_constrains_the_model_with_the_schema_and_a_low_temperature(self, post):
        post.return_value = ollama_response('{"value": 1}')

        ask_ollama("system prompt", "user prompt", Tiny)

        sent = post.call_args.kwargs["json"]
        assert sent["model"] == MODEL
        assert sent["system"] == "system prompt"
        assert sent["prompt"] == "user prompt"
        assert sent["stream"] is False
        # the schema is what stops the model free-forming prose instead of JSON
        assert sent["format"] == Tiny.model_json_schema()
        # determinism matters more than creativity for a classifier
        assert sent["options"]["temperature"] == 0.1

    @patch("app.analysis.llm_evaluator.requests.post")
    def test_malformed_json_becomes_a_value_error(self, post):
        post.return_value = ollama_response("I think the game is delayed!")

        with pytest.raises(ValueError, match="invalid evaluation format"):
            ask_ollama("system", "user", Tiny)

    @patch("app.analysis.llm_evaluator.requests.post")
    def test_schema_violation_becomes_a_value_error(self, post):
        """Well-formed JSON that does not satisfy the model is still a failure."""
        post.return_value = ollama_response('{"value": "not-a-number"}')

        with pytest.raises(ValueError):
            ask_ollama("system", "user", Tiny)

    @patch("app.analysis.llm_evaluator.requests.post")
    def test_unreachable_ollama_becomes_a_runtime_error(self, post):
        post.side_effect = requests.exceptions.ConnectionError("connection refused")

        with pytest.raises(RuntimeError, match="Failed to communicate with Ollama"):
            ask_ollama("system", "user", Tiny)

    @patch("app.analysis.llm_evaluator.requests.post")
    def test_http_error_status_becomes_a_runtime_error(self, post):
        response = MagicMock()
        response.raise_for_status.side_effect = requests.exceptions.HTTPError("500")
        post.return_value = response

        with pytest.raises(RuntimeError):
            ask_ollama("system", "user", Tiny)


class TestEvaluatePost:
    @patch("app.analysis.llm_evaluator.requests.post")
    def test_returns_a_parsed_post_evaluation(self, post):
        post.return_value = ollama_response(VALID_EVALUATION)

        result = evaluate_post("Hollow Knight: Silksong", "Team Cherry pushed the date again.")

        assert isinstance(result, PostEvaluation)
        assert result.info_type is InformationType.RUMOR
        assert result.mentions_delay is True
        assert result.sentiment_score == -0.8
        assert result.confidence == 0.9

    @patch("app.analysis.llm_evaluator.requests.post")
    def test_sends_both_the_title_and_the_post_body(self, post):
        post.return_value = ollama_response(VALID_EVALUATION)

        evaluate_post("Silksong", "some news text")

        prompt = post.call_args.kwargs["json"]["prompt"]
        assert "Silksong" in prompt
        assert "some news text" in prompt

    @patch("app.analysis.llm_evaluator.requests.post")
    def test_grounds_the_system_prompt_in_todays_date(self, post):
        """Without this, the model has no anchor for a date given without a year and
        hallucinates one from training data instead."""
        post.return_value = ollama_response(VALID_EVALUATION)

        evaluate_post("Silksong", "some news text")

        system_prompt = post.call_args.kwargs["json"]["system"]
        assert date.today().isoformat() in system_prompt


class TestConfidenceNormalisation:
    """
    Small models routinely answer "confidence: 85" when asked for 0..1. The validator rescales
    instead of rejecting, because a rejected evaluation costs a whole post.
    """

    @pytest.mark.parametrize(
        "raw, expected",
        [(0.9, 0.9), (1.0, 1.0), (8.5, 0.85), (85, 0.85), (95.0, 0.95)],
    )
    def test_out_of_range_confidence_is_rescaled(self, raw, expected):
        evaluation = PostEvaluation(
            reasoning="r",
            sentiment_score=-0.5,
            mentions_delay=True,
            info_type=InformationType.RUMOR,
            confidence=raw,
        )
        assert evaluation.confidence == pytest.approx(expected)


class TestExtractedReleaseDateNormalisation:
    """
    A local model asked for a date routinely answers in prose ("around Q3 2027") or gets the
    format slightly wrong - that must null the field, not reject the whole evaluation the way
    confidence rescaling avoids rejecting over a bad number.
    """

    def _evaluation(self, extracted_release_date):
        return PostEvaluation(
            reasoning="r",
            sentiment_score=0.2,
            mentions_delay=False,
            info_type=InformationType.RELEASE_DATE_CHANGE,
            confidence=0.9,
            extracted_release_date=extracted_release_date,
        )

    def test_a_valid_iso_date_is_kept(self):
        assert self._evaluation("2027-03-15").extracted_release_date == "2027-03-15"

    def test_none_stays_none(self):
        assert self._evaluation(None).extracted_release_date is None

    def test_defaults_to_none_when_omitted(self):
        evaluation = PostEvaluation(
            reasoning="r", sentiment_score=0.2, mentions_delay=False,
            info_type=InformationType.RELEASE_DATE_CHANGE, confidence=0.9,
        )
        assert evaluation.extracted_release_date is None

    @pytest.mark.parametrize("bad_value", ["around Q3 2027", "next year", "2027-13-40", "", "   "])
    def test_a_malformed_or_vague_value_is_nulled_not_rejected(self, bad_value):
        assert self._evaluation(bad_value).extracted_release_date is None

    def test_a_hallucinated_wrong_year_is_nulled(self):
        """The real bug this guards against: a trailer caption with no year at all
        ("In Theaters October 2") gets a plausible-looking but wrong year attached."""
        wrong_year = date(date.today().year - 3, 10, 2).isoformat()
        assert self._evaluation(wrong_year).extracted_release_date is None

    def test_a_date_just_past_the_plausible_past_bound_is_nulled(self):
        too_old = (date.today() - MAX_PLAUSIBLE_PAST - timedelta(days=1)).isoformat()
        assert self._evaluation(too_old).extracted_release_date is None

    def test_a_date_just_inside_the_plausible_past_bound_is_kept(self):
        just_old_enough = (date.today() - MAX_PLAUSIBLE_PAST + timedelta(days=1)).isoformat()
        assert self._evaluation(just_old_enough).extracted_release_date == just_old_enough

    def test_a_date_just_past_the_plausible_future_bound_is_nulled(self):
        too_far = (date.today() + MAX_PLAUSIBLE_FUTURE + timedelta(days=1)).isoformat()
        assert self._evaluation(too_far).extracted_release_date is None

    def test_a_date_just_inside_the_plausible_future_bound_is_kept(self):
        just_soon_enough = (date.today() + MAX_PLAUSIBLE_FUTURE - timedelta(days=1)).isoformat()
        assert self._evaluation(just_soon_enough).extracted_release_date == just_soon_enough

    @patch("app.analysis.llm_evaluator.requests.post")
    def test_evaluate_post_surfaces_the_extracted_date(self, post):
        payload = """{
            "reasoning": "The studio confirmed the date.",
            "sentiment_score": 0.5,
            "mentions_delay": false,
            "info_type": "release_date_change",
            "confidence": 0.9,
            "extracted_release_date": "2027-06-01"
        }"""
        post.return_value = ollama_response(payload)

        result = evaluate_post("Hollow Knight: Silksong", "It's official: June 1st, 2027.")

        assert result.extracted_release_date == "2027-06-01"
