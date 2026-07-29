import json
import logging
import requests
from typing import Type, TypeVar
from pydantic import BaseModel, ValidationError

from app.schemas import InformationType, PostEvaluation, SourceReputationEvaluation

# ============================================================
# CONSTANTS & CONFIGURATION
# ============================================================

OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen2.5:7b"

KNOWN_SOURCES_REPUTATION = {
    "bloomberg": 0.95,
    "ign": 0.90,
    "gamespot": 0.85,
    "kotaku": 0.80,
    "eurogamer": 0.85,
    "polygon": 0.80,
    "gematsu": 0.80,
    "vg247": 0.75,
    "destructoid": 0.75,
    "pc gamer": 0.80,
    "gamesradar+": 0.75,
    "insider gaming": 0.75,
    "dexerto": 0.70,
    "comicbook.com": 0.60,
    "reddit": 0.50,
    "4chan": 0.10
}

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

Always think step-by-step in the 'reasoning' field before providing the final classification."""

SYSTEM_PROMPT_SOURCE_EVAL = """You are an expert in the video game and media journalism industry.
Your task is to evaluate the reliability and reputation of a given news source.
Score the source on a scale from 0.0 to 1.0, where:
- 0.95 - 1.0: Top-tier, industry-leading journalists (e.g., Bloomberg).
- 0.8 - 0.9: Highly reputable gaming news outlets (e.g., IGN, Eurogamer).
- 0.5 - 0.7: Mixed reliability, often posts rumors or clickbait (e.g., general Reddit, obscure blogs).
- 0.0 - 0.4: Highly unreliable, anonymous boards, or known fake news sites (e.g., 4chan).

If you do not recognize the source, assign it a default score of 0.4 and state that it is unknown.
Always think step-by-step in the 'reasoning' field before providing the final score."""

# ============================================================
# FUNCTIONS
# ============================================================

T = TypeVar('T', bound=BaseModel)


def ask_ollama(system_prompt: str, user_prompt: str, response_schema: Type[T]) -> tuple[T, str]:
    """
    A universal wrapper for sending requests to Ollama and receiving a Pydantic object.
    Returns a tuple: (Parsed Pydantic object, raw JSON string from the model)
    """
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

        parsed_object = response_schema.model_validate_json(raw)
        return parsed_object, raw

    except ValidationError as e:
        raise ValueError(f"LLM returned invalid evaluation format: {raw}") from e
    except requests.exceptions.RequestException as e:
        raise RuntimeError(f"Failed to communicate with Ollama at {OLLAMA_URL}: {e}") from e


def get_source_reputation(source_name: str) -> float:
    """
    Determines the reputation score of a news source.
    First, checks the hardcoded curated list. If not found, uses the LLM to evaluate it.
    """
    source_lower = source_name.lower().strip()

    # 1. Fast path: Check the curated list
    for known_source, score in KNOWN_SOURCES_REPUTATION.items():
        if known_source in source_lower:
            logging.info(f"Source '{source_name}' found in curated list. Score: {score}")
            return score

    # 2. LLM Evaluation path
    logging.info(f"Source '{source_name}' not in curated list. Asking LLM for evaluation...")
    prompt = f'Evaluate the reputation of this gaming/media news source: "{source_name}"'

    try:
        evaluation, _ = ask_ollama(SYSTEM_PROMPT_SOURCE_EVAL, prompt, SourceReputationEvaluation)

        logging.info(
            f"LLM evaluated '{source_name}' at {evaluation.reputation_score}. Reasoning: {evaluation.reasoning}"
        )
        return evaluation.reputation_score

    except Exception as e:
        logging.error(f"Failed to get LLM evaluation for source '{source_name}': {e}. Defaulting to 0.4")
        return 0.4


def evaluate_post(media_title: str, post_text: str) -> PostEvaluation:
    """
    Evaluates a specific news post snippet using the local LLM to determine
    if it contains news about a release date delay.
    """
    print(f"\n{'=' * 50}")
    print(f"🔄 ANALYSIS: [{media_title}]")
    print(f"📰 TEXT: {post_text[:150]}...")

    prompt = f'Title: "{media_title}"\nPost: "{post_text}"'

    try:
        evaluation, raw_json = ask_ollama(SYSTEM_PROMPT_POST_EVAL, prompt, PostEvaluation)

        try:
            formatted_json = json.dumps(json.loads(raw_json), indent=2, ensure_ascii=False)

            formatted_json = formatted_json.replace('"unrelated"', '\033[91m"unrelated"\033[0m')
            formatted_json = formatted_json.replace('"rumor"', '\033[93m"rumor"\033[0m')
            formatted_json = formatted_json.replace('"release_date_change"', '\033[92m"release_date_change"\033[0m')

            print(f"🧠 MODEL'S ANSWER:\n{formatted_json}")
        except json.JSONDecodeError:
            print(f"🧠 MODEL'S ANSWER:\n{raw_json}")

        color_map = {
            InformationType.UNRELATED: "\033[91m",  # Red
            InformationType.RUMOR: "\033[93m",  # Yellow
            InformationType.RELEASE_DATE_CHANGE: "\033[92m"  # Green
        }
        color = color_map.get(evaluation.info_type, "\033[0m")
        reset_color = "\033[0m"

        print(f"💡 LOGIC: {evaluation.reasoning}")
        print(
            f"🏷️ STATUS: {color}{evaluation.info_type.value.upper()}{reset_color} | Confidence: {evaluation.confidence}")
        return evaluation

    except Exception as e:
        raise e
