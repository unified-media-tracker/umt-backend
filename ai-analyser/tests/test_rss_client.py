"""
The curated feeds are fixed and untrusted-format XML from the outside world - the only real
logic here is the title/summary substring match that narrows a general feed down to entries
about one specific media item, and that one dead feed must not take the others down with it.
"""
from unittest.mock import patch

from app.ingestion.rss_client import fetch_posts


class FakeEntry(dict):
    """Mimics feedparser's FeedParserDict enough for these tests: dict-style .get() and
    attribute access, with hasattr(entry, "published_parsed") reflecting whether the key
    was actually set."""

    def __getattr__(self, name):
        try:
            return self[name]
        except KeyError:
            raise AttributeError(name)


class FakeFeed:
    def __init__(self, entries):
        self.entries = entries


def entry(title, summary="", link="https://example.com/a", published_parsed=None):
    data = {"title": title, "summary": summary, "link": link}
    if published_parsed is not None:
        data["published_parsed"] = published_parsed
    return FakeEntry(data)


class TestFetchPosts:
    @patch("app.ingestion.rss_client.feedparser.parse")
    def test_only_entries_mentioning_the_title_are_kept(self, parse):
        parse.return_value = FakeFeed([
            entry("Silksong finally gets a release date"),
            entry("Unrelated article about something else"),
        ])

        posts = fetch_posts("Silksong")

        # one matching entry per curated feed (11 feeds configured)
        assert len(posts) == 11
        assert all("Silksong" in p["text"] for p in posts)

    @patch("app.ingestion.rss_client.feedparser.parse")
    def test_match_is_case_insensitive_and_checks_the_summary_too(self, parse):
        parse.return_value = FakeFeed([
            entry("Big news today", summary="silksong delayed again"),
        ])

        posts = fetch_posts("SILKSONG")

        assert len(posts) == 11

    @patch("app.ingestion.rss_client.feedparser.parse")
    def test_respects_limit_per_feed(self, parse):
        parse.return_value = FakeFeed([entry("Silksong update") for _ in range(10)])

        posts = fetch_posts("Silksong", limit_per_feed=2)

        # 2 per feed * 11 feeds
        assert len(posts) == 22

    @patch("app.ingestion.rss_client.feedparser.parse")
    def test_a_feed_that_raises_does_not_take_down_the_others(self, parse):
        def side_effect(url):
            if "eurogamer" in url:
                raise RuntimeError("network blew up")
            return FakeFeed([entry("Silksong news")])

        parse.side_effect = side_effect

        posts = fetch_posts("Silksong")

        # 10 of the 11 feeds succeed
        assert len(posts) == 10

    @patch("app.ingestion.rss_client.feedparser.parse")
    def test_missing_published_parsed_does_not_crash(self, parse):
        parse.return_value = FakeFeed([entry("Silksong news", published_parsed=None)])

        posts = fetch_posts("Silksong")

        assert all(isinstance(p["published_at"], float) for p in posts)


class TestMediaTypeFiltering:
    """There's no point asking a gaming outlet about a book or a movie outlet about an
    album - media_type narrows which feeds are even queried, down to the ones covering
    that type of media."""

    @patch("app.ingestion.rss_client.feedparser.parse")
    def test_a_game_only_queries_the_three_gaming_feeds(self, parse):
        parse.return_value = FakeFeed([entry("Silksong news")])

        fetch_posts("Silksong", media_type="GAME")

        queried_urls = [call.args[0] for call in parse.call_args_list]
        assert len(queried_urls) == 3
        assert all(any(name in url for name in ("ign", "eurogamer", "gamespot")) for url in queried_urls)

    @patch("app.ingestion.rss_client.feedparser.parse")
    def test_a_movie_only_queries_the_three_movie_tv_feeds(self, parse):
        parse.return_value = FakeFeed([entry("Some Movie news")])

        fetch_posts("Some Movie", media_type="MOVIE")

        assert parse.call_count == 3

    @patch("app.ingestion.rss_client.feedparser.parse")
    def test_a_book_only_queries_the_two_book_feeds(self, parse):
        parse.return_value = FakeFeed([entry("Some Novel news")])

        fetch_posts("Some Novel", media_type="BOOK")

        queried_urls = [call.args[0] for call in parse.call_args_list]
        assert len(queried_urls) == 2
        assert all(any(name in url for name in ("lithub", "bookriot")) for url in queried_urls)

    @patch("app.ingestion.rss_client.feedparser.parse")
    def test_a_music_only_queries_the_three_music_feeds(self, parse):
        parse.return_value = FakeFeed([entry("Some Album news")])

        fetch_posts("Some Album", media_type="MUSIC")

        queried_urls = [call.args[0] for call in parse.call_args_list]
        assert len(queried_urls) == 3
        assert all(any(name in url for name in ("pitchfork", "billboard", "rollingstone")) for url in queried_urls)

    @patch("app.ingestion.rss_client.feedparser.parse")
    def test_media_type_none_queries_every_feed(self, parse):
        parse.return_value = FakeFeed([entry("Silksong news")])

        fetch_posts("Silksong", media_type=None)

        assert parse.call_count == 11
