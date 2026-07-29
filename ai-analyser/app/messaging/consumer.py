import json
import pika
import logging
from uuid import UUID
from app.common.config import settings
from app.pipeline import run_pipeline_for_media_item

log = logging.getLogger(__name__)

EXCHANGE = "umt.events"
QUEUE = "ai-analyser.media-imported"
ROUTING_KEY = "media.imported"


def start_consumer():
    connection = pika.BlockingConnection(
        pika.ConnectionParameters(
            host=settings.rabbitmq_host,
            credentials=pika.PlainCredentials(settings.rabbitmq_user, settings.rabbitmq_password),
        )
    )
    channel = connection.channel()

    channel.exchange_declare(exchange=EXCHANGE, exchange_type="topic", durable=True)
    channel.queue_declare(queue=QUEUE, durable=True)
    channel.queue_bind(exchange=EXCHANGE, queue=QUEUE, routing_key=ROUTING_KEY)

    def callback(ch, method, properties, body):
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

            log.info("Received media.imported event for %s", media_item_id)

            run_pipeline_for_media_item(media_item_id, title)
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception:
            log.exception("Error processing message: %s", body)
            ch.basic_ack(delivery_tag=method.delivery_tag)

    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=QUEUE, on_message_callback=callback)

    log.info("Started RabbitMQ consumer for %s", QUEUE)
    channel.start_consuming()
