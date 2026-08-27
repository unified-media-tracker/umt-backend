from unittest.mock import patch

from app.ingestion.google_news_client import fetch_posts


class FakeEntry(dict):
    def __getattr__(self, name):
        try:
            return self[name]
        except KeyError:
            raise AttributeError(name)


class FakeFeed:
    def __init__(self, entries):
        self.entries = entries


def entry(title="Some article", summary="", link="https://example.com/a", source_title="Mashable",
          published_parsed=None):
    data = {"title": title, "summary": summary, "link": link, "source": {"title": source_title}}
    if published_parsed is not None:
        data["published_parsed"] = published_parsed
    return FakeEntry(data)


class TestQueryConstruction:
    @patch("app.ingestion.google_news_client.feedparser.parse")
    def test_dashes_in_the_title_are_stripped_not_left_as_an_exclusion_operator(self, parse):
        parse.return_value = FakeFeed([])

        fetch_posts("Re:ZERO -Starting Life in Another World-")

        queried_url = parse.call_args.args[0]
        assert "-Starting" not in queried_url
        assert "Starting" in queried_url

    @patch("app.ingestion.google_news_client.feedparser.parse")
    def test_the_query_is_not_wrapped_in_exact_phrase_quotes(self, parse):
        parse.return_value = FakeFeed([])

        fetch_posts("Grand Theft Auto VI")

        queried_url = parse.call_args.args[0]
        assert "%22" not in queried_url  # URL-encoded double quote


class TestFetchPosts:
    @patch("app.ingestion.google_news_client.feedparser.parse")
    def test_maps_entries_into_the_house_post_shape(self, parse):
        parse.return_value = FakeFeed([entry(title="GTA 6 delayed again", source_title="Mashable")])

        posts = fetch_posts("Grand Theft Auto VI")

        assert len(posts) == 1
        assert posts[0]["source_name"] == "Mashable"
        assert "GTA 6 delayed again" in posts[0]["text"]


    @patch("app.ingestion.google_news_client.feedparser.parse")
    def test_missing_source_title_falls_back_to_google_news(self, parse):
        e = entry()
        del e["source"]

        parse.return_value = FakeFeed([e])

        posts = fetch_posts("Grand Theft Auto VI")

        assert posts[0]["source_name"] == "Google News"

    @patch("app.ingestion.google_news_client.feedparser.parse")
    def test_missing_published_parsed_does_not_crash(self, parse):
        parse.return_value = FakeFeed([entry(published_parsed=None)])

        posts = fetch_posts("Grand Theft Auto VI")

        assert isinstance(posts[0]["published_at"], float)

    @patch("app.ingestion.google_news_client.feedparser.parse")
    def test_media_type_is_accepted_but_does_not_change_the_query(self, parse):
        parse.return_value = FakeFeed([])

        fetch_posts("Grand Theft Auto VI", media_type="GAME")
        fetch_posts("Grand Theft Auto VI", media_type="BOOK")

        assert parse.call_args_list[0].args[0] == parse.call_args_list[1].args[0]
