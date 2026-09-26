"""
The other end of these messages is Kotlin, so a mismatch fails silently: a renamed queue or routing
key just means every analysis request is dropped. The core-service pins the same names in
RabbitMQConfigTest.
"""
from app.messaging.consumer import (
    EXCHANGE,
    MEDIA_ANALYSIS_REQUESTED_QUEUE,
    MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY,
    MEDIA_RELEASED_QUEUE,
    MEDIA_RELEASED_ROUTING_KEY,
)


def test_the_names_match_what_core_service_publishes_and_declares():
    assert EXCHANGE == "umt.events"
    assert MEDIA_ANALYSIS_REQUESTED_ROUTING_KEY == "media.analysis.requested"
    assert MEDIA_ANALYSIS_REQUESTED_QUEUE == "ai-analyser.media-analysis-requested"
    assert MEDIA_RELEASED_ROUTING_KEY == "media.released"
    assert MEDIA_RELEASED_QUEUE == "ai-analyser.media-released"
