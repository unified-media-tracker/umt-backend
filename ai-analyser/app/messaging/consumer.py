import json
import pika
import logging
from datetime import date
from uuid import UUID
from app.common.config import settings
from app.db.session import SessionLocal
from app.messaging.outcome_consumer import handle_media_released
from app.pipeline import run_pipeline_for_media_item

log = logging.getLogger(__name__)

EXCHANGE = "umt.events"
MEDIA_ANALYSIS_REQUESTED_QUEUE = "ai-analyser.media-analysis-requested"
MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY = "media.analysis.requested"
MEDIA_RELEASED_QUEUE = "ai-analyser.media-released"
MEDIA_RELEASED_ROUTING_KEY = "media.released"


def start_consumer():
    connection = pika.BlockingConnection(
        pika.ConnectionParameters(
            host=settings.rabbitmq_host,
            credentials=pika.PlainCredentials(settings.rabbitmq_user, settings.rabbitmq_password),
        )
    )
    channel = connection.channel()

    channel.exchange_declare(exchange=EXCHANGE, exchange_type="topic", durable=True)

    channel.queue_declare(queue=MEDIA_ANALYSIS_REQUESTED_QUEUE, durable=True)
    channel.queue_bind(exchange=EXCHANGE, queue=MEDIA_ANALYSIS_REQUESTED_QUEUE, routing_key=MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY)

    channel.queue_declare(queue=MEDIA_RELEASED_QUEUE, durable=True)
    channel.queue_bind(exchange=EXCHANGE, queue=MEDIA_RELEASED_QUEUE, routing_key=MEDIA_RELEASED_ROUTING_KEY)

    def media_analysis_requested_callback(ch, method, properties, body):
        try:
            payload = json.loads(body)

            media_item_id_str = payload.get("media_item_id")
            if not media_item_id_str:
                log.error("Message missing media_item_id: %s", payload)
                ch.basic_ack(delivery_tag=method.delivery_tag)
                return
            media_item_id = UUID(media_item_id_str)

            title = payload.get("title")
            if not title:
                log.error("Message missing media title: %s", payload)
                ch.basic_ack(delivery_tag=method.delivery_tag)
                return

            # Both optional - older/manual messages may not carry them. media_category=None
            # queries every ingestion source; known_release_date=None just means date-based
            # delay detection is skipped for this run, the same as before either field existed.
            media_category = payload.get("media_category")
            known_release_date = payload.get("release_date")

            log.info("Received media.analysis.requested event for %s", media_item_id)

            run_pipeline_for_media_item(
                media_item_id, title, media_category=media_category, known_release_date=known_release_date,
            )
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception:
            log.exception("Error processing media.analysis.requested message: %s", body)
            ch.basic_ack(delivery_tag=method.delivery_tag)

    def media_released_callback(ch, method, properties, body):
        try:
            payload = json.loads(body)

            media_item_id_str = payload.get("media_item_id")
            actual_release_date_str = payload.get("actual_release_date")
            if not media_item_id_str or not actual_release_date_str:
                log.error("Message missing media_item_id or actual_release_date: %s", payload)
                ch.basic_ack(delivery_tag=method.delivery_tag)
                return

            log.info("Received media.released event for %s", media_item_id_str)

            session = SessionLocal()
            try:
                handle_media_released(
                    UUID(media_item_id_str), date.fromisoformat(actual_release_date_str), session,
                )
            finally:
                session.close()
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception:
            log.exception("Error processing media.released message: %s", body)
            ch.basic_ack(delivery_tag=method.delivery_tag)

    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=MEDIA_ANALYSIS_REQUESTED_QUEUE, on_message_callback=media_analysis_requested_callback)
    channel.basic_consume(queue=MEDIA_RELEASED_QUEUE, on_message_callback=media_released_callback)

    log.info("Started RabbitMQ consumer for %s and %s", MEDIA_ANALYSIS_REQUESTED_QUEUE, MEDIA_RELEASED_QUEUE)
    channel.start_consuming()
