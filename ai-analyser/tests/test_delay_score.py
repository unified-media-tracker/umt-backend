"""
compute_delay_probability is the one number this whole service exists to produce, and it is
pure — no DB, no network, no LLM. That makes it the cheapest thing in the codebase to pin down
and the most expensive thing to get silently wrong.
"""
import pytest

from app.analysis.delay_score import (
    SENTIMENT_WEIGHT,
    VOLUME_SATURATION_POINT,
    VOLUME_WEIGHT,
    compute_delay_probability,
)
from app.schemas import InformationType, RumorSignalInput


def signal(
    *,
    sentiment=-1.0,
    reputation=1.0,
    confidence=1.0,
    mentions_delay=True,
    info_type=InformationType.RUMOR,
    source_name="Eurogamer",
):
    return RumorSignalInput(
        source_name=source_name,
        sentiment_score=sentiment,
        source_reputation_score=reputation,
        mentions_delay=mentions_delay,
        evaluation_confidence=confidence,
        info_type=info_type,
    )


def official_delay(confidence=0.9, sentiment=-0.7):
    return signal(
        info_type=InformationType.RELEASE_DATE_CHANGE,
        mentions_delay=True,
        sentiment=sentiment,
        confidence=confidence,
    )


class TestOfficialDelayShortCircuit:
    """An official, confidently negative date change is certainty, not evidence."""

    def test_official_delay_returns_certainty(self):
        assert compute_delay_probability([official_delay()]) == 100.0

    def test_official_delay_outweighs_any_number_of_calm_rumors(self):
        signals = [official_delay()] + [
            signal(sentiment=0.9, mentions_delay=False) for _ in range(50)
        ]
        assert compute_delay_probability(signals) == 100.0

    def test_confidence_threshold_is_strict(self):
        """confidence must be > 0.8, not >= — 0.8 exactly is not enough."""
        assert compute_delay_probability([official_delay(confidence=0.8)]) == 0.0
        assert compute_delay_probability([official_delay(confidence=0.81)]) == 100.0

    def test_positive_sentiment_is_not_a_delay(self):
        """A confirmed date being *moved up* is a release_date_change too."""
        assert compute_delay_probability([official_delay(sentiment=0.5)]) == 0.0

    def test_neutral_sentiment_is_not_a_delay(self):
        assert compute_delay_probability([official_delay(sentiment=0.0)]) == 0.0


class TestNoRelevantSignals:
    def test_empty_input(self):
        assert compute_delay_probability([]) == 0.0

    def test_unrelated_posts_are_ignored(self):
        signals = [signal(info_type=InformationType.UNRELATED) for _ in range(10)]
        assert compute_delay_probability(signals) == 0.0

    def test_rumors_that_do_not_mention_a_delay_are_ignored(self):
        signals = [signal(mentions_delay=False) for _ in range(10)]
        assert compute_delay_probability(signals) == 0.0

    def test_release_date_change_without_delay_flag_is_not_counted_as_a_rumor(self):
        signals = [
            signal(info_type=InformationType.RELEASE_DATE_CHANGE, mentions_delay=False)
        ]
        assert compute_delay_probability(signals) == 0.0


class TestRumorScoring:
    def test_single_maximally_negative_rumor(self):
        # negativity 1.0 * weight 0.6, volume 1/20 = 0.05 * weight 0.4  ->  0.62
        assert compute_delay_probability([signal()]) == 62.0

    def test_low_reputation_and_low_confidence_dampen_the_score(self):
        # 0.5 * 0.8 * 0.5 = 0.2 negativity, volume 0.05  ->  0.6*0.2 + 0.4*0.05 = 0.14
        result = compute_delay_probability(
            [signal(sentiment=-0.5, reputation=0.8, confidence=0.5)]
        )
        assert result == 14.0

    def test_positive_sentiment_contributes_no_negativity(self):
        """max(0.0, -sentiment) floors a positive-sentiment rumor at zero, it never subtracts."""
        # only the volume term survives: 0.4 * (1/20) = 0.02
        assert compute_delay_probability([signal(sentiment=0.9)]) == 2.0

    def test_volume_saturates_at_the_saturation_point(self):
        at_point = [signal() for _ in range(VOLUME_SATURATION_POINT)]
        beyond = [signal() for _ in range(VOLUME_SATURATION_POINT * 5)]

        assert compute_delay_probability(at_point) == 100.0
        assert compute_delay_probability(beyond) == 100.0

    def test_score_never_exceeds_one_hundred(self):
        signals = [signal() for _ in range(200)]
        assert compute_delay_probability(signals) <= 100.0

    def test_negativity_is_averaged_not_summed(self):
        """Ten identical rumors must not be ten times as negative as one."""
        one = [signal(sentiment=-0.5, reputation=1.0, confidence=1.0)]
        ten = [signal(sentiment=-0.5, reputation=1.0, confidence=1.0) for _ in range(10)]

        negativity_component_one = compute_delay_probability(one) - 100 * VOLUME_WEIGHT * (1 / 20)
        negativity_component_ten = compute_delay_probability(ten) - 100 * VOLUME_WEIGHT * (10 / 20)

        assert negativity_component_one == pytest.approx(negativity_component_ten, abs=1e-9)
        assert negativity_component_one == pytest.approx(100 * SENTIMENT_WEIGHT * 0.5, abs=1e-9)

    def test_result_is_rounded_to_two_decimals(self):
        result = compute_delay_probability([signal(sentiment=-1 / 3, confidence=1 / 7)])
        assert result == round(result, 2)

    def test_unrelated_signals_do_not_dilute_the_average(self):
        """Only rumor-with-delay signals form the denominator."""
        with_noise = [signal()] + [signal(info_type=InformationType.UNRELATED) for _ in range(19)]
        assert compute_delay_probability(with_noise) == compute_delay_probability([signal()])
