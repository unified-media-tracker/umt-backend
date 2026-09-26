"""
The other end of these messages is Kotlin, so a mismatch fails silently: a renamed queue or routing
key just means every analysis request is dropped. The core-service pins the same names in
RabbitMQConfigTest.
"""
from unittest.mock import patch

from app.messaging.consumer import (
    EXCHANGE,
    MEDIA_ANALYSIS_REQUESTED_QUEUE,
    MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY,
    MEDIA_ANALYSIS_REQUESTED_TTL_MS,
    MEDIA_RELEASED_QUEUE,
    MEDIA_RELEASED_ROUTING_KEY,
    start_consumer,
)


def test_the_names_match_what_core_service_publishes_and_declares():
    assert EXCHANGE == "umt.events"
    assert MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY == "media.analysis.requested"
    assert MEDIA_ANALYSIS_REQUESTED_QUEUE == "ai-analyser.media-analysis-requested"
    assert MEDIA_RELEASED_ROUTING_KEY == "media.released"
    assert MEDIA_RELEASED_QUEUE == "ai-analyser.media-released"


def test_the_request_queue_is_declared_with_the_ttl_core_service_declares_it_with():
    """RabbitMQ rejects a second declaration whose arguments differ, so a drift here is a startup failure."""
    assert MEDIA_ANALYSIS_REQUESTED_TTL_MS == 86_400_000  # core-service: MEDIA_ANALYSIS_REQUESTED_TTL_MS

    with patch("app.messaging.consumer.pika.BlockingConnection") as connection:
        start_consumer()

    channel = connection.return_value.channel.return_value
    declared = {call.kwargs["queue"]: call.kwargs for call in channel.queue_declare.call_args_list}
    assert declared[MEDIA_ANALYSIS_REQUESTED_QUEUE]["arguments"] == {"x-message-ttl": 86_400_000}
    assert "arguments" not in declared[MEDIA_RELEASED_QUEUE]  # only the request queue expires
