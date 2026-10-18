"""
media_category became required on the Kotlin side once movie/tv_show/game/book/music split into five
independent tables - a snapshot with no known type has nowhere left to be stored, so publishing
one would just make the consumer reject it. The one behaviour worth pinning down here is that a
missing media_category is caught before anything goes on the wire, not after.
"""
from unittest.mock import MagicMock, patch
from uuid import uuid4

from app.messaging.publisher import publish_rumor_computed


class TestPublishRumorComputed:
    @patch("app.messaging.publisher.pika")
    def test_skips_publishing_when_media_category_is_missing(self, mock_pika):
        publish_rumor_computed(
            media_item_id=uuid4(),
            delay_probability=42.0,
            aggregate_sentiment_score=0.1,
            top_source_name="IGN",
            media_category=None,
        )

        mock_pika.BlockingConnection.assert_not_called()

    @patch("app.messaging.publisher.pika")
    def test_includes_media_category_in_the_published_payload(self, mock_pika):
        channel = MagicMock()
        mock_pika.BlockingConnection.return_value.channel.return_value = channel
        media_item_id = uuid4()

        publish_rumor_computed(
            media_item_id=media_item_id,
            delay_probability=42.0,
            aggregate_sentiment_score=0.1,
            top_source_name="IGN",
            media_category="GAME",
            confidence_trend="RISING",
        )

        channel.basic_publish.assert_called_once()
        body = channel.basic_publish.call_args.kwargs["body"]
        assert '"media_category": "GAME"' in body
        assert str(media_item_id) in body
