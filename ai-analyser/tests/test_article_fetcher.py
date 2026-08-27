from unittest.mock import MagicMock, patch

import requests

from app.ingestion.article_fetcher import fetch_full_text


def html_response(text="<html><body>ok</body></html>"):
    response = MagicMock()
    response.raise_for_status.return_value = None
    response.text = text
    return response


class TestSuccessfulExtraction:
    @patch("app.ingestion.article_fetcher.trafilatura.extract")
    @patch("app.ingestion.article_fetcher.requests.get")
    def test_returns_the_extracted_article_when_long_enough(self, get, extract):
        get.return_value = html_response()
        extract.return_value = "A" * 500

        result = fetch_full_text("https://example.com/article", fallback="short snippet")

        assert result == "A" * 500


class TestFallback:
    @patch("app.ingestion.article_fetcher.trafilatura.extract")
    @patch("app.ingestion.article_fetcher.requests.get")
    def test_falls_back_when_extraction_is_too_thin(self, get, extract):
        get.return_value = html_response()
        extract.return_value = "too short"

        result = fetch_full_text("https://example.com/video", fallback="the rss snippet")

        assert result == "the rss snippet"

    @patch("app.ingestion.article_fetcher.trafilatura.extract")
    @patch("app.ingestion.article_fetcher.requests.get")
    def test_falls_back_when_extraction_returns_none(self, get, extract):
        get.return_value = html_response()
        extract.return_value = None

        result = fetch_full_text("https://example.com/paywalled", fallback="the rss snippet")

        assert result == "the rss snippet"

    @patch("app.ingestion.article_fetcher.trafilatura.extract")
    def test_falls_back_on_a_request_timeout(self, extract):
        with patch("app.ingestion.article_fetcher.requests.get", side_effect=requests.exceptions.Timeout):
            result = fetch_full_text("https://example.com/slow", fallback="the rss snippet")

        assert result == "the rss snippet"
        extract.assert_not_called()

    @patch("app.ingestion.article_fetcher.requests.get")
    def test_falls_back_on_an_http_error_status(self, get):
        response = MagicMock()
        response.raise_for_status.side_effect = requests.exceptions.HTTPError("404")
        get.return_value = response

        result = fetch_full_text("https://example.com/missing", fallback="the rss snippet")

        assert result == "the rss snippet"

    @patch("app.ingestion.article_fetcher.requests.get")
    def test_falls_back_when_trafilatura_itself_raises(self, get):
        get.return_value = html_response()

        with patch("app.ingestion.article_fetcher.trafilatura.extract", side_effect=Exception("parser bug")):
            result = fetch_full_text("https://example.com/weird", fallback="the rss snippet")

        assert result == "the rss snippet"


class TestNonArticleDomains:
    @patch("app.ingestion.article_fetcher.trafilatura.extract")
    @patch("app.ingestion.article_fetcher.requests.get")
    def test_a_youtube_watch_url_is_skipped_without_a_network_call(self, get, extract):
        result = fetch_full_text("https://www.youtube.com/watch?v=abc123", fallback="title. description")

        assert result == "title. description"
        get.assert_not_called()
        extract.assert_not_called()

    @patch("app.ingestion.article_fetcher.trafilatura.extract")
    @patch("app.ingestion.article_fetcher.requests.get")
    def test_a_youtu_be_short_link_is_also_skipped(self, get, extract):
        result = fetch_full_text("https://youtu.be/abc123", fallback="title. description")

        assert result == "title. description"
        get.assert_not_called()
