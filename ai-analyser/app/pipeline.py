from app.analysis.llm_evaluator import evaluate_post
from app.analysis.delay_score import RumorSignalInput, compute_delay_probability
from app.db.models import RumorSignal
from app.db.session import SessionLocal
from app.ingestion.google_news_client import fetch_posts
from app.messaging.publisher import publish_rumor_computed
from datetime import datetime, timezone
from sqlalchemy import text
from uuid import UUID
import logging

logger = logging.getLogger(__name__)


def run_pipeline_for_media_item(media_item_id: UUID):
    try:
        session = SessionLocal()
    except Exception:
        session = None
    try:
        # Fetch media title from a core database
        if session:
            result = session.execute(
                text("SELECT title FROM media_item WHERE id = :id"),
                {"id": media_item_id}
            ).fetchone()
        else:
            result = None

        if not result:
            logger.error("Media item %s not found", media_item_id)
            return

        media_title = result[0]
        run_pipeline_for_title(media_item_id, media_title)

    except Exception:
        logger.exception("Pipeline failed for media item %s", media_item_id)
    finally:
        if session:
            session.close()


def run_pipeline_for_title(media_item_id: UUID, media_title: str, publish: bool = True):
    try:
        session = SessionLocal()
    except Exception:
        session = None
    try:
        logger.info("Starting analysis for: %s (%s)", media_title, media_item_id)

        # 1. Fetch posts from social media
        raw_posts = fetch_posts(media_title)

        # 2. Process and score
        delay_probability = process_raw_posts(media_item_id, media_title, raw_posts)

        # 3. Publish results
        # Simple aggregation
        if session:
            signals = session.query(RumorSignal).filter(RumorSignal.media_item_id == media_item_id).all()
        else:
            signals = []
        avg_sentiment = sum(s.sentiment_score for s in signals) / len(signals) if signals else None

        # Determine confidence trend (placeholder for now)
        confidence_trend = "STABLE"

        # Find the top source name
        top_source = None
        if signals:
            top_source = max(signals, key=lambda s: s.source_reputation_score).source_name

        if publish:
            publish_rumor_computed(
                media_item_id=media_item_id,
                delay_probability=delay_probability,
                aggregate_sentiment_score=avg_sentiment,
                confidence_trend=confidence_trend,
                top_source_name=top_source,
            )
        else:
            logger.info("[MOCK] Results for %s: Delay=%s%%, Sentiment=%s, Top Source=%s",
                        media_title, delay_probability, avg_sentiment, top_source)

        logger.info("Analysis completed for %s. Delay Probability: %s%%", media_title, delay_probability)

    except Exception:
        logger.exception("Pipeline failed for title %s", media_title)
    finally:
        if session:
            session.close()


def process_raw_posts(media_item_id, media_title: str, raw_posts: list[dict]):
    try:
        session = SessionLocal()
    except Exception:
        session = None
    signals: list[RumorSignalInput] = []

    for post in raw_posts:
        try:
            evaluation = evaluate_post(media_title, post["text"])
        except Exception:
            logger.exception("LLM evaluation failed for post from %s", post["source_url"])
            continue

        if session:
            session.add(RumorSignal(
            media_item_id=media_item_id,
            source_name=post["source_name"],
            source_url=post["source_url"],
            source_reputation_score=post["source_reputation_score"],
            sentiment_score=evaluation.sentiment_score,
            mentions_delay=evaluation.mentions_delay,
            info_type=evaluation.info_type,
            evaluation_confidence=evaluation.confidence,
            published_at=datetime.fromtimestamp(post["published_at"], tz=timezone.utc),
            ingested_at=datetime.now(timezone.utc),
        ))

        signals.append(RumorSignalInput(
            sentiment_score=evaluation.sentiment_score,
            source_reputation_score=post["source_reputation_score"],
            mentions_delay=evaluation.mentions_delay,
            evaluation_confidence=evaluation.confidence,
            info_type=evaluation.info_type,
        ))

    if session:
        session.commit()
    return compute_delay_probability(signals)
