import json
import pika

from datetime import datetime, timezone
from uuid import UUID
from app.common.config import settings

EXCHANGE = "umt.events"
ROUTING_KEY = "rumor.snapshot.computed"


def publish_rumor_computed(
        media_item_id: UUID,
        delay_probability: float,
        aggregate_sentiment_score: float | None,
        top_source_name: str | None,
):
    if pika is None:
        raise ImportError("pika is not installed or has a syntax error in this environment.")

    connection = pika.BlockingConnection(
        pika.ConnectionParameters(
            host=settings.rabbitmq_host,
            credentials=pika.PlainCredentials(settings.rabbitmq_user, settings.rabbitmq_password),
        )
    )
    channel = connection.channel()
    channel.exchange_declare(exchange=EXCHANGE, exchange_type="topic", durable=True)

    payload = {
        "media_item_id": str(media_item_id),
        "delay_probability": delay_probability,
        "aggregate_sentiment_score": aggregate_sentiment_score,
        "top_source_name": top_source_name,
        "computed_at": datetime.now(timezone.utc).isoformat(),
    }

    channel.basic_publish(
        exchange=EXCHANGE,
        routing_key=ROUTING_KEY,
        body=json.dumps(payload),
        properties=pika.BasicProperties(content_type="application/json", delivery_mode=2),
    )
    connection.close()
