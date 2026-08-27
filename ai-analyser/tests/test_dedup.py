"""
Dedup exists to collapse reposts of the same underlying story before they inflate the delay
score's volume factor or cost extra LLM calls. The one thing it must never do is lose data:
a post whose embedding can't be computed has to survive un-deduped, not vanish.
"""
from unittest.mock import patch

from app.analysis.dedup import deduplicate_posts, _cosine_similarity


def post(text, source_url="https://example.com/a"):
    return {"text": text, "source_url": source_url}


class TestCosineSimilarity:
    def test_identical_vectors_are_maximally_similar(self):
        assert _cosine_similarity([1.0, 0.0], [1.0, 0.0]) == 1.0

    def test_orthogonal_vectors_are_not_similar(self):
        assert _cosine_similarity([1.0, 0.0], [0.0, 1.0]) == 0.0

    def test_a_zero_vector_does_not_divide_by_zero(self):
        assert _cosine_similarity([0.0, 0.0], [1.0, 0.0]) == 0.0


class TestDeduplicatePosts:
    @patch("app.analysis.dedup.get_embedding")
    def test_near_duplicate_posts_collapse_to_one(self, get_embedding):
        # two near-identical embeddings and one clearly different
        get_embedding.side_effect = [
            [1.0, 0.0],
            [0.99, 0.01],
            [0.0, 1.0],
        ]

        result = deduplicate_posts([post("story A"), post("story A reworded"), post("story B")])

        assert len(result) == 2

    @patch("app.analysis.dedup.get_embedding")
    def test_dissimilar_posts_are_all_kept(self, get_embedding):
        get_embedding.side_effect = [
            [1.0, 0.0],
            [0.0, 1.0],
            [0.0, -1.0]
        ]

        result = deduplicate_posts([post("a"), post("b"), post("c")])

        assert len(result) == 3

    @patch("app.analysis.dedup.get_embedding")
    def test_a_post_whose_embedding_fails_is_kept_unchanged(self, get_embedding):
        get_embedding.side_effect = [
            [1.0, 0.0],
            RuntimeError("ollama down")
        ]

        result = deduplicate_posts([post("story A"), post("story B", source_url="https://example.com/b")])

        assert len(result) == 2

    @patch("app.analysis.dedup.get_embedding")
    def test_respects_a_custom_threshold(self, get_embedding):
        # the similarity here is ~0.995 - collapses at the default 0.92 threshold but not at 0.999
        get_embedding.side_effect = [
            [1.0, 0.0],
            [0.995, 0.0999]
        ]

        result = deduplicate_posts([post("a"), post("b")], threshold=0.999)

        assert len(result) == 2

    def test_an_empty_list_returns_an_empty_list(self):
        assert deduplicate_posts([]) == []
