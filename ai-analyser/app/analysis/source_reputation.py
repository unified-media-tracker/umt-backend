import logging
from sqlalchemy.orm import Session

from app.analysis.llm_evaluator import ask_ollama
from app.db.models import SourceReputation
from app.schemas import SourceReputationEvaluation

log = logging.getLogger(__name__)

SYSTEM_PROMPT_SOURCE_EVAL = """You are an expert in the video game and media journalism industry.
Your task is to evaluate the reliability and reputation of a given news source.
Score the source on a scale from 0.0 to 1.0, where:
- 0.95 - 1.0: Top-tier, industry-leading journalists (e.g., Bloomberg).
- 0.8 - 0.9: Highly reputable gaming news outlets (e.g., IGN, Eurogamer).
- 0.5 - 0.7: Mixed reliability, often posts rumors or clickbait (e.g., general Reddit, obscure blogs).
- 0.0 - 0.4: Highly unreliable, anonymous boards, or known fake news sites (e.g., 4chan).

If you do not recognize the source, assign it a default score of 0.4 and state that it is unknown.
Always think step-by-step in the 'reasoning' field before providing the final score."""


def get_source_reputation(source_name: str, session: Session) -> float:
    """
    Determines the reputation score of a news source.
    First, checks the hardcoded curated list. If not found, uses the LLM to evaluate it.
    """
    normalized = source_name.lower().strip()

    existing = (
        session.query(SourceReputation)
        .filter(SourceReputation.source_name == normalized)
        .first()
    )
    if existing:
        log.debug("Source '%s' found in DB, score=%s", source_name, existing.reputation_score)
        return float(existing.reputation_score)

    log.info("Source '%s' not in DB, asking LLM...", source_name)
    prompt = f'Evaluate the reputation of this gaming/media news source: "{source_name}"'

    try:
        evaluation, _ = ask_ollama(SYSTEM_PROMPT_SOURCE_EVAL, prompt, SourceReputationEvaluation)
        session.add(SourceReputation(
            source_name=normalized,
            reputation_score=evaluation.reputation_score,
            is_curated=False,
            reasoning=evaluation.reasoning,
        ))
        session.commit()
        log.info("Persisted new source '%s' at %.2f", source_name, evaluation.reputation_score)
        return evaluation.reputation_score

    except Exception:
        log.exception("Failed to get LLM evaluation for '%s', defaulting to 0.4", source_name)
        return 0.4
