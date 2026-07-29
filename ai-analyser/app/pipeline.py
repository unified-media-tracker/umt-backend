from app.analysis.llm_evaluator import evaluate_post
from app.analysis.delay_score import RumorSignalInput, compute_delay_probability
from app.analysis.source_reputation import get_source_reputation
from app.db.models import RumorSignal
from app.db.session import SessionLocal
from app.ingestion.google_news_client import fetch_posts
from app.messaging.publisher import publish_rumor_computed
from datetime import datetime, timezone
from uuid import UUID
import logging

from app.schemas import InformationType

log = logging.getLogger(__name__)


def run_pipeline_for_media_item(media_item_id: UUID, media_title: str, publish: bool = True) -> None:
    session = SessionLocal()
    try:
        log.info("Starting analysis for: %s (%s)", media_title, media_item_id)

        raw_posts = fetch_posts(media_title)
        delay_probability, signals = process_raw_posts(session, media_item_id, media_title, raw_posts)

        relevant = [s for s in signals if s.info_type != InformationType.UNRELATED]
        avg_sentiment = (
            sum(s.sentiment_score for s in relevant) / len(relevant) if relevant else None
        )
        top_source = (
            max(relevant, key=lambda s: s.source_reputation_score).source_name if relevant else None
        )

        if publish:
            publish_rumor_computed(media_item_id=media_item_id, delay_probability=delay_probability,
                                   aggregate_sentiment_score=avg_sentiment, top_source_name=top_source)
        else:
            log.info(
                "[MOCK] Results for %s: Delay=%s%%, Sentiment=%s, Top Source=%s",
                media_title, delay_probability, avg_sentiment, top_source,
            )

        log.info("Analysis completed for %s. Delay Probability: %s%%", media_title, delay_probability)

    except Exception:
        log.exception("Pipeline failed for title %s", media_title)
    finally:
        session.close()


def process_raw_posts(
        session, media_item_id, media_title: str, raw_posts: list[dict]
) -> tuple[float, list[RumorSignalInput]]:
    signals: list[RumorSignalInput] = []

    for post in raw_posts:
        reputation = get_source_reputation(post["source_name"], session)

        try:
            evaluation = evaluate_post(media_title, post["text"])
        except Exception:
            log.exception("LLM evaluation failed for post from %s", post["source_url"])
            continue

        session.add(RumorSignal(
            media_item_id=media_item_id,
            source_name=post["source_name"],
            source_url=post["source_url"],
            source_reputation_score=reputation,
            sentiment_score=evaluation.sentiment_score,
            mentions_delay=evaluation.mentions_delay,
            info_type=evaluation.info_type,
            evaluation_confidence=evaluation.confidence,
            published_at=datetime.fromtimestamp(post["published_at"], tz=timezone.utc),
            ingested_at=datetime.now(timezone.utc),
        ))

        signals.append(RumorSignalInput(
            source_name=post["source_name"],
            sentiment_score=evaluation.sentiment_score,
            source_reputation_score=reputation,
            mentions_delay=evaluation.mentions_delay,
            evaluation_confidence=evaluation.confidence,
            info_type=evaluation.info_type,
        ))

    session.commit()
    return compute_delay_probability(signals), signals
