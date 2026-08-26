from app.analysis.llm_evaluator import evaluate_post
from app.analysis.delay_score import RumorSignalInput, compute_delay_probability
from app.analysis.dedup import deduplicate_posts
from app.analysis.source_reputation import get_source_reputation
from app.analysis.confidence_trend import compute_trend, record_snapshot
from app.db.models import RumorSignal
from app.db.session import SessionLocal
from app.ingestion.aggregator import fetch_all_posts
from app.ingestion.article_fetcher import fetch_full_text
from app.messaging.publisher import publish_rumor_computed
from datetime import date, datetime, timezone
from uuid import UUID
import logging

from app.schemas import InformationType

log = logging.getLogger(__name__)


def run_pipeline_for_media_item(
        media_item_id: UUID, media_title: str, media_type: str | None = None,
        known_release_date: str | None = None, publish: bool = True,
) -> None:
    session = SessionLocal()
    try:
        log.info("Starting analysis for: %s (%s)", media_title, media_item_id)

        parsed_known_date = date.fromisoformat(known_release_date) if known_release_date else None

        raw_posts = fetch_all_posts(media_title, media_type=media_type)
        delay_probability, signals = process_raw_posts(
            session, media_item_id, media_title, raw_posts, parsed_known_date,
        )

        relevant = [s for s in signals if s.info_type != InformationType.UNRELATED]
        avg_sentiment = (
            sum(s.sentiment_score for s in relevant) / len(relevant) if relevant else None
        )
        top_source = (
            max(relevant, key=lambda s: s.source_reputation_score).source_name if relevant else None
        )

        record_snapshot(session, media_item_id, delay_probability, len(signals))
        trend = compute_trend(session, media_item_id)

        if publish:
            publish_rumor_computed(media_item_id=media_item_id, delay_probability=delay_probability,
                                   aggregate_sentiment_score=avg_sentiment, top_source_name=top_source,
                                   confidence_trend=trend)
        else:
            log.info(
                "[MOCK] Results for %s: Delay=%s%%, Sentiment=%s, Top Source=%s, Trend=%s",
                media_title, delay_probability, avg_sentiment, top_source, trend,
            )

        log.info("Analysis completed for %s. Delay Probability: %s%%", media_title, delay_probability)

    except Exception:
        log.exception("Pipeline failed for title %s", media_title)
    finally:
        session.close()


def process_raw_posts(
        session, media_item_id, media_title: str, raw_posts: list[dict],
        known_release_date: date | None = None,
) -> tuple[float, list[RumorSignalInput]]:
    signals: list[RumorSignalInput] = []
    deduped_posts = deduplicate_posts(raw_posts)

    for post in deduped_posts:
        reputation = get_source_reputation(post["source_name"], session)
        full_text = fetch_full_text(post["source_url"], fallback=post["text"])

        try:
            evaluation = evaluate_post(media_title, full_text)
        except Exception:
            log.exception("LLM evaluation failed for post from %s", post["source_url"])
            continue

        extracted_date = (
            date.fromisoformat(evaluation.extracted_release_date)
            if evaluation.extracted_release_date else None
        )

        session.add(RumorSignal(
            media_item_id=media_item_id,
            source_name=post["source_name"],
            source_url=post["source_url"],
            source_reputation_score=reputation,
            sentiment_score=evaluation.sentiment_score,
            mentions_delay=evaluation.mentions_delay,
            info_type=evaluation.info_type,
            evaluation_confidence=evaluation.confidence,
            extracted_release_date=extracted_date,
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
            extracted_release_date=extracted_date,
        ))

    session.commit()
    return compute_delay_probability(signals, known_release_date), signals
