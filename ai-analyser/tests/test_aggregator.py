"""
The aggregator's only job is fan-out with isolation: every configured source contributes its
posts to one list, and a source blowing up must not cost the others their results.
"""
from unittest.mock import patch

from app.ingestion.aggregator import fetch_all_posts


class TestFetchAllPosts:
    @patch("app.ingestion.aggregator.youtube_client")
    @patch("app.ingestion.aggregator.rss_client")
    @patch("app.ingestion.aggregator.google_news_client")
    def test_concatenates_posts_from_every_source(self, google_news, rss, youtube):
        google_news.fetch_posts.return_value = [{"source_name": "Google News"}]
        rss.fetch_posts.return_value = [{"source_name": "IGN"}, {"source_name": "Variety"}]
        youtube.fetch_posts.return_value = [{"source_name": "SomeChannel"}]

        posts = fetch_all_posts("Silksong")

        assert len(posts) == 4
        source_names = {p["source_name"] for p in posts}
        assert source_names == {"Google News", "IGN", "Variety", "SomeChannel"}

    @patch("app.ingestion.aggregator.youtube_client")
    @patch("app.ingestion.aggregator.rss_client")
    @patch("app.ingestion.aggregator.google_news_client")
    def test_one_source_failing_does_not_lose_the_others(self, google_news, rss, youtube):
        google_news.fetch_posts.side_effect = RuntimeError("network blew up")
        rss.fetch_posts.return_value = [{"source_name": "IGN"}]
        youtube.fetch_posts.return_value = [{"source_name": "SomeChannel"}]

        posts = fetch_all_posts("Silksong")

        assert len(posts) == 2

    @patch("app.ingestion.aggregator.youtube_client")
    @patch("app.ingestion.aggregator.rss_client")
    @patch("app.ingestion.aggregator.google_news_client")
    def test_all_sources_failing_yields_an_empty_list_not_an_exception(self, google_news, rss, youtube):
        google_news.fetch_posts.side_effect = RuntimeError("boom")
        rss.fetch_posts.side_effect = RuntimeError("boom")
        youtube.fetch_posts.side_effect = RuntimeError("boom")

        assert fetch_all_posts("Silksong") == []

    @patch("app.ingestion.aggregator.youtube_client")
    @patch("app.ingestion.aggregator.rss_client")
    @patch("app.ingestion.aggregator.google_news_client")
    def test_media_category_is_passed_to_every_source(self, google_news, rss, youtube):
        google_news.fetch_posts.return_value = []
        rss.fetch_posts.return_value = []
        youtube.fetch_posts.return_value = []

        fetch_all_posts("Silksong", media_category="GAME")

        google_news.fetch_posts.assert_called_once_with("Silksong", media_category="GAME")
        rss.fetch_posts.assert_called_once_with("Silksong", media_category="GAME")
        youtube.fetch_posts.assert_called_once_with("Silksong", media_category="GAME")
