"""
The LLM is the least trustworthy component in the service: it is a local 7B model asked to
return structured JSON. These tests pin the contract at the boundary — a bad response has to
surface as a typed error, never as a half-parsed object that reaches the database.
"""
from unittest.mock import MagicMock, patch

import pytest
import requests
from pydantic import BaseModel

from app.analysis.llm_evaluator import MODEL, ask_ollama, evaluate_post
from app.schemas import InformationType, PostEvaluation


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
