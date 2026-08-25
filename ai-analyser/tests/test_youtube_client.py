"""
YouTube ingestion's job is purely mapping the API's response shape into
the house post shape - videoId -> a watch URL, channelTitle ->source_name,
and a malformed item must not take the whole batch down.
"""
from unittest.mock import MagicMock, patch

import app.ingestion.youtube_client as youtube_client
from app.ingestion.youtube_client import fetch_posts


def api_response(items):
    response = MagicMock()
    response.raise_for_status.return_value = None
    response.json.return_value = {"items": items}
    return response


def video_item(video_id="abc123", title="Silksong release date leaked", description="details here",
                channel="GameLeaksChannel", published_at="2027-01-01T00:00:00Z"):
    return {
        "id": {"videoId": video_id},
        "snippet": {
            "title": title,
            "description": description,
            "channelTitle": channel,
            "publishedAt": published_at,
        },
    }


class TestNoApiKey:
    def test_returns_empty_list_and_does_not_call_the_api(self):
        with patch.object(youtube_client.settings, "youtube_api_key", ""):
            with patch("app.ingestion.youtube_client.requests.get") as get:
                posts = fetch_posts("Silksong")

        assert posts == []
        get.assert_not_called()


class TestWithApiKey:
    @patch("app.ingestion.youtube_client.requests.get")
    def test_maps_a_video_item_into_the_house_post_shape(self, get):
        get.return_value = api_response([video_item()])

        with patch.object(youtube_client.settings, "youtube_api_key", "test-key"):
            posts = fetch_posts("Silksong")

        assert len(posts) == 1
        post = posts[0]
        assert post["source_name"] == "GameLeaksChannel"
        assert post["source_url"] == "https://www.youtube.com/watch?v=abc123"
        assert "Silksong release date leaked" in post["text"]
        assert "details here" in post["text"]

    @patch("app.ingestion.youtube_client.requests.get")
    def test_sends_the_title_as_part_of_the_search_query(self, get):
        get.return_value = api_response([])

        with patch.object(youtube_client.settings, "youtube_api_key", "test-key"):
            fetch_posts("Silksong")

        sent = get.call_args.kwargs["params"]
        assert "Silksong" in sent["q"]
        assert sent["key"] == "test-key"
        assert sent["type"] == "video"

    @patch("app.ingestion.youtube_client.requests.get")
    def test_an_item_missing_video_id_is_skipped_without_crashing(self, get):
        broken = {"id": {}, "snippet": {"title": "x", "channelTitle": "c", "publishedAt": "2027-01-01T00:00:00Z"}}
        get.return_value = api_response([broken, video_item()])

        with patch.object(youtube_client.settings, "youtube_api_key", "test-key"):
            posts = fetch_posts("Silksong")

        assert len(posts) == 1

    @patch("app.ingestion.youtube_client.requests.get")
    def test_an_unparsable_published_at_falls_back_instead_of_crashing(self, get):
        get.return_value = api_response([video_item(published_at="not-a-date")])

        with patch.object(youtube_client.settings, "youtube_api_key", "test-key"):
            posts = fetch_posts("Silksong")

        assert isinstance(posts[0]["published_at"], float)
