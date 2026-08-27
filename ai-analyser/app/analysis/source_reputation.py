import logging
from sqlalchemy.orm import Session

from app.analysis.llm_evaluator import ask_ollama
from app.db.models import SourceReputation
from app.schemas import SourceReputationEvaluation

log = logging.getLogger(__name__)

SYSTEM_PROMPT_SOURCE_EVAL = """You are an expert in the video game and media journalism industry.
Your task is to evaluate the reliability and reputation of a given news source.
Score the source on a scale from 0.0 to 1.0, where:
- 0.95 - 1.0: Top-tier wire services / industry-leading journalism. Rigorous editorial
  standards, corrections are rare and immediate.
  Examples: Bloomberg, Reuters, Associated Press (AP), The Wall Street Journal,
  The New York Times, BBC.
- 0.8 - 0.9: Highly reputable trade press and established outlets. Strong editorial
  standards; may report unconfirmed rumors but clearly labels them as such and is well-sourced.
  Movie/TV: Variety, The Hollywood Reporter, Deadline, Empire, Entertainment Weekly,
  IndieWire, Collider, The Wrap.
  Games: IGN, Eurogamer, GameSpot, Polygon, Kotaku, PC Gamer, Game Informer,
  GamesIndustry.biz, VG247, Rock Paper Shotgun, Nintendo Life, Push Square, Digital Foundry,
  Famitsu, 4Gamer.net.
  Tech/general with a gaming/entertainment beat: Ars Technica, The Verge.
- 0.5 - 0.7: Mixed reliability. Decent original reporting mixed with clickbait, aggregation
  of other outlets' work, or occasional unverified scoops.
  Screen Rant, CBR, GamesRadar+, Destructoid, TheGamer, ComicBook.com, GameRant,
  Insider Gaming, WCCFTech, We Got This Covered, Forbes (contributor pieces vary widely),
  Business Insider, MSN (mostly re-published aggregation, rarely original), Reddit
  (e.g. r/movies, r/Games - community discussion, not vetted reporting), ResetEra, NeoGAF.
- 0.0 - 0.4: Highly unreliable. No editorial accountability, frequent fabrication, or
  anonymous/unverified claims.
  4chan, unverified leak accounts on X/Twitter or TikTok, small or unverified YouTube "leak"
  channels, anonymous fan wikis, generic no-byline clickbait aggregator blogs.

The lists above are not exhaustive - use them to calibrate, not as the only sources you can
score. If you do not recognize the source and it doesn't clearly fit one of these patterns,
assign it a default score of 0.4 and state that it is unknown.
Always think step-by-step in the 'reasoning' field before providing the final score."""


def get_source_reputation(source_name: str, session: Session) -> float:
    """
    Determines the reputation score of a news source.
    First, checks the DB cache (source_reputation). If not found, asks the LLM, which itself
    is primed with a curated tier list of well-known outlets in SYSTEM_PROMPT_SOURCE_EVAL
    to calibrate against.
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
