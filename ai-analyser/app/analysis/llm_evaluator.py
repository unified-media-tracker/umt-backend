import logging
import requests

from datetime import date
from typing import Type, TypeVar
from pydantic import BaseModel, ValidationError

from app.schemas import PostEvaluation

log = logging.getLogger(__name__)

# ============================================================
# CONSTANTS & CONFIGURATION
# ============================================================

OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen2.5:7b"

# ============================================================
# PROMPTS
# ============================================================

SYSTEM_PROMPT_POST_EVAL = """You are a media-industry analyst. Given a news snippet about a movie or video game,
evaluate if it provides information about its release date (new announcements, delays, confirmations, or rumors).
Classify the information as:
1. 'rumor': if the release date info is speculative, from unverified insiders, or explicitly called a leak/rumor.
2. 'release_date_change': if it is an OFFICIAL announcement of a release date, a confirmation of a release date, or a delay/pushback. (Treat ANY official release date news as this category).
3. 'unrelated': if it does not discuss release dates at all.

Provide a sentiment score (-1.0 to 1.0) regarding the release date:
- Negative (-1.0 to -0.1): Delay, pushback, or development trouble.
- Neutral (0.0): General discussion.
- Positive (0.1 to 1.0): Official date confirmation, early release, or game going gold.

Today's date is {today}. If the post explicitly names a specific release date (a day, or at
minimum a month and year), extract it into 'extracted_release_date' as an ISO 8601 date
(YYYY-MM-DD). Use the 1st of the month when only a month and year are given. If a day/month is
given WITHOUT a year, infer the year using today's date: pick the nearest occurrence of that
day on or after today, since release-date news is almost always about what's ahead, not what's
already past. Never guess a year from habit - ground it in today's date or the post's own text.
Leave the field null if no specific date is mentioned (e.g. only a vague window like "next year"
or "Q3").

Always think step-by-step in the 'reasoning' field before providing the final classification."""

# ============================================================
# FUNCTIONS
# ============================================================

T = TypeVar('T', bound=BaseModel)


def ask_ollama(system_prompt: str, user_prompt: str, response_schema: Type[T]) -> tuple[T, str]:
    """
    A universal wrapper for sending requests to Ollama and receiving a Pydantic object.
    Returns a tuple: (Parsed Pydantic object, raw JSON string from the model)
    """
    raw: str | None = None
    try:
        response = requests.post(
            OLLAMA_URL,
            json={
                "model": MODEL,
                "system": system_prompt,
                "prompt": user_prompt,
                "format": response_schema.model_json_schema(),
                "stream": False,
                "options": {"temperature": 0.1},
            },
            timeout=30,
        )
        response.raise_for_status()
        raw = response.json()["response"]
        return response_schema.model_validate_json(raw), raw

    except ValidationError as e:
        raise ValueError(f"LLM returned invalid evaluation format: {raw}") from e
    except requests.exceptions.RequestException as e:
        raise RuntimeError(f"Failed to communicate with Ollama at {OLLAMA_URL}: {e}") from e


def evaluate_post(media_title: str, post_text: str) -> PostEvaluation:
    """
    Evaluates a specific news post using the local LLM to determine
    if it contains news about a release date delay.
    """
    log.debug("ANALYSIS: [%s]", media_title)
    log.debug("TEXT: %s...", post_text[:150])

    system_prompt = SYSTEM_PROMPT_POST_EVAL.format(today=date.today().isoformat())
    prompt = f'Title: "{media_title}"\nPost: "{post_text}"'
    evaluation, raw_json = ask_ollama(system_prompt, prompt, PostEvaluation)

    log.debug("MODEL'S RAW ANSWER: %s", raw_json)
    log.debug("REASONING: %s", evaluation.reasoning)
    log.debug(
        "STATUS: %s | confidence=%.2f",
        evaluation.info_type.value.upper(),
        evaluation.confidence,
    )
    return evaluation
