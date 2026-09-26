"""
compute_delay_probability is the one number this whole service exists to produce, and it is
pure — no DB, no network, no LLM. That makes it the cheapest thing in the codebase to pin down
and the most expensive thing to get silently wrong.
"""
import functools
from datetime import date, timedelta

import pytest

from app.analysis.delay_probability import (
    BASELINE_DISTANT,
    BASELINE_IMMINENT,
    DISTANT_DAYS,
    FULL_WEIGHT_SHIFT_DAYS,
    IMMINENT_DAYS,
    MAX_CONFIRMATION_STRENGTH,
    MIN_MEANINGFUL_SHIFT_DAYS,
    NO_SIGNAL_BASELINE,
    SENTIMENT_WEIGHT,
    SOLO_CONFIRMATION_REPUTATION_THRESHOLD,
    VOLUME_SATURATION_POINT,
    VOLUME_WEIGHT,
    baseline_for,
    compute_delay_probability as _compute_delay_probability,
)
from app.schemas import InformationType, RumorSignalInput

# The no-evidence prior depends on how far off the release is, so every call below happens on a
# fixed day rather than the real clock. This one sits 166 days before the 2026-11-19 release the
# classes use - exactly where the distance ramp lands on the 5.0 default the pinned arithmetic
# was written against. TestDistanceToRelease covers the ramp itself, passing its own `today`.
TODAY = date(2026, 11, 19) - timedelta(days=166)
compute_delay_probability = functools.partial(_compute_delay_probability, today=TODAY)


def signal(
        *,
        sentiment=-1.0,
        reputation=1.0,
        confidence=1.0,
        mentions_delay=True,
        info_type=InformationType.RUMOR,
        source_name="Eurogamer",
        extracted_release_date=None,
):
    return RumorSignalInput(
        source_name=source_name,
        sentiment_score=sentiment,
        source_reputation_score=reputation,
        mentions_delay=mentions_delay,
        evaluation_confidence=confidence,
        info_type=info_type,
        extracted_release_date=extracted_release_date,
    )


def official_delay(confidence=0.9, sentiment=-0.7):
    return signal(
        info_type=InformationType.RELEASE_DATE_CHANGE,
        mentions_delay=True,
        sentiment=sentiment,
        confidence=confidence,
    )


class TestOfficialDelayShortCircuit:
    """
    An official, confidently negative date change dominates the sentiment-based rumor scoring
    below - but even then the result is continuous "confirmation strength" (source quality,
    with a bonus for independent agreement), never a flat, literal 100/0.
    """

    def test_official_delay_dominates_the_score(self):
        # avg_quality = reputation 1.0 * confidence 0.9 = 0.9, no agreement bonus (one source) -> 90.0
        assert compute_delay_probability([official_delay()]) == 90.0

    def test_official_delay_outweighs_any_number_of_calm_rumors(self):
        signals = [official_delay()] + [
            signal(sentiment=0.9, mentions_delay=False) for _ in range(50)
        ]
        assert compute_delay_probability(signals) == 90.0

    def test_confidence_threshold_is_strict(self):
        """confidence must be > 0.8, not >= — 0.8 exactly doesn't even clear corroboration,
        so it falls all the way through to the no-signal baseline."""
        assert compute_delay_probability([official_delay(confidence=0.8)]) == NO_SIGNAL_BASELINE
        assert compute_delay_probability([official_delay(confidence=0.81)]) == 81.0  # 1.0 * 0.81

    def test_positive_sentiment_is_not_a_delay(self):
        """A confirmed date being *moved up* is a release_date_change too."""
        assert compute_delay_probability([official_delay(sentiment=0.5)]) == NO_SIGNAL_BASELINE

    def test_neutral_sentiment_is_not_a_delay(self):
        assert compute_delay_probability([official_delay(sentiment=0.0)]) == NO_SIGNAL_BASELINE


class TestKnownReleaseDateComparison:
    """
    An LLM-extracted date is only news once compared against what we already knew - a post
    stating the same date we already have on record isn't a signal at all - just noise.
    """

    KNOWN = date(2026, 11, 19)

    def confirmed(self, extracted, confidence=0.9):
        return signal(
            info_type=InformationType.RELEASE_DATE_CHANGE,
            extracted_release_date=extracted,
            confidence=confidence,
            mentions_delay=False,
            sentiment=1.0,
        )

    def test_a_later_extracted_date_is_a_confirmed_delay(self):
        # avg_quality = 1.0 * 0.9 = 0.9 -> 90.0
        result = compute_delay_probability([self.confirmed(date(2027, 3, 1))], known_release_date=self.KNOWN)
        assert result == 90.0

    def test_an_earlier_extracted_date_is_a_confirmed_move_up(self):
        # the opposite of a delay: baseline 5.0 * (1 - 0.9 confirmation strength) -> 0.5
        result = compute_delay_probability([self.confirmed(date(2026, 6, 1))], known_release_date=self.KNOWN)
        assert result == 0.5

    def test_a_matching_extracted_date_contributes_no_signal_beyond_normal_scoring(self):
        baseline = compute_delay_probability([signal()], known_release_date=self.KNOWN)
        with_matching_confirmation = compute_delay_probability([signal(), self.confirmed(self.KNOWN)],
                                                               known_release_date=self.KNOWN, )
        assert with_matching_confirmation == baseline

    def test_low_confidence_does_not_trigger_the_comparison(self):
        low_confidence = self.confirmed(date(2027, 3, 1), confidence=0.8)
        result = compute_delay_probability([low_confidence], known_release_date=self.KNOWN)
        assert result == NO_SIGNAL_BASELINE

    def test_a_rumor_with_an_extracted_date_does_not_trigger_the_comparison(self):
        rumored = signal(
            info_type=InformationType.RUMOR, extracted_release_date=date(2027, 3, 1),
            confidence=0.95, mentions_delay=True, sentiment=-0.8,
        )
        result = compute_delay_probability([rumored], known_release_date=self.KNOWN)
        assert result != 100.0
        assert result != 0.0

    def test_a_later_confirmation_wins_over_a_conflicting_earlier_one(self):
        signals = [self.confirmed(date(2027, 3, 1)), self.confirmed(date(2026, 6, 1))]
        assert compute_delay_probability(signals, known_release_date=self.KNOWN) == 90.0


class TestCorroborationRequirement:
    """
    A single post - however, confidently classified - can be one hallucinated date or one
    low-credibility upload away from being wrong. Dominating the score now needs either a
    source reputable enough to stand alone or independent sources agreeing; without that it
    falls back to the no-signal baseline (for a pure date claim) or the weighted rumor average
    (for delay-flavored wording), never a flat 100/0 forced by one flimsy post.
    """

    KNOWN = date(2026, 11, 19)

    def confirmed(self, extracted, reputation, source_name="source"):
        return signal(
            info_type=InformationType.RELEASE_DATE_CHANGE,
            extracted_release_date=extracted,
            confidence=0.9,
            mentions_delay=False,
            sentiment=1.0,
            reputation=reputation,
            source_name=source_name,
        )

    def test_reputation_at_the_solo_threshold_confirms_alone_but_not_at_full_strength(self):
        high_rep = self.confirmed(date(2027, 3, 1), reputation=SOLO_CONFIRMATION_REPUTATION_THRESHOLD)
        # avg_quality = 0.8 * 0.9 = 0.72, no agreement bonus (one source) -> 72.0
        assert compute_delay_probability([high_rep], known_release_date=self.KNOWN) == 72.0

    def test_just_below_the_solo_threshold_needs_corroboration(self):
        almost = self.confirmed(date(2027, 3, 1), reputation=SOLO_CONFIRMATION_REPUTATION_THRESHOLD - 0.01)
        assert compute_delay_probability([almost], known_release_date=self.KNOWN) == NO_SIGNAL_BASELINE

    def test_two_independent_low_reputation_sources_agreeing_do_corroborate(self):
        signals = [
            self.confirmed(date(2027, 3, 1), reputation=0.4, source_name="Channel A"),
            self.confirmed(date(2027, 3, 5), reputation=0.4, source_name="Channel B"),
        ]
        # avg_quality 0.4*0.9=0.36, +0.05 agreement bonus for the 2nd independent source -> 41.0
        assert compute_delay_probability(signals, known_release_date=self.KNOWN) == 41.0

    def test_the_same_low_reputation_source_repeated_is_not_corroboration(self):
        signals = [
            self.confirmed(date(2027, 3, 1), reputation=0.4, source_name="Channel A"),
            self.confirmed(date(2027, 3, 5), reputation=0.4, source_name="Channel A"),
        ]
        result = compute_delay_probability(signals, known_release_date=self.KNOWN)
        assert result == NO_SIGNAL_BASELINE

    def test_an_uncorroborated_date_only_confirmation_falls_back_to_the_baseline(self):
        """This signal carries no delay-flavored sentiment to blend into the weighted average
        (mentions_delay=False) - rejecting it should fall through to the no-signal baseline,
        not to the opposite hard extreme."""
        low_rep = self.confirmed(date(2027, 3, 1), reputation=0.4)
        result = compute_delay_probability([low_rep], known_release_date=self.KNOWN)
        assert result == NO_SIGNAL_BASELINE

    def test_confirmation_strength_is_capped_below_full_certainty(self):
        """However overwhelming the evidence, the result never claims literal certainty."""
        overwhelming = [
            self.confirmed(date(2027, 3, 1), reputation=1.0, source_name=f"Outlet {i}")
            for i in range(5)
        ]
        result = compute_delay_probability(overwhelming, known_release_date=self.KNOWN)
        assert result == MAX_CONFIRMATION_STRENGTH * 100

    def test_the_agreement_bonus_has_diminishing_returns(self):
        def sources(n):
            return [
                self.confirmed(date(2027, 3, 1), reputation=0.5, source_name=f"Outlet {i}")
                for i in range(n)
            ]

        two = compute_delay_probability(sources(2), known_release_date=self.KNOWN)
        three = compute_delay_probability(sources(3), known_release_date=self.KNOWN)
        four = compute_delay_probability(sources(4), known_release_date=self.KNOWN)
        eight = compute_delay_probability(sources(8), known_release_date=self.KNOWN)

        assert two < three < four  # each additional independent source still helps...
        assert four == eight  # ...until the bonus caps out at 4 sources

    def test_weak_evidence_of_an_earlier_date_never_raises_the_delay_probability(self):
        """Used to mirror as 100 - strength, so two weak sources saying "earlier" read as a 59% delay."""
        signals = [
            self.confirmed(date(2026, 6, 1), reputation=0.4, source_name="Channel A"),
            self.confirmed(date(2026, 6, 1), reputation=0.4, source_name="Channel B"),
        ]
        # baseline 5.0 * (1 - 0.41 strength) -> 2.95
        assert compute_delay_probability(signals, known_release_date=self.KNOWN) == 2.95

    def test_official_delay_branch_also_requires_corroboration(self):
        low_rep_delay = signal(
            info_type=InformationType.RELEASE_DATE_CHANGE, mentions_delay=True,
            sentiment=-0.7, confidence=0.9, reputation=0.4, source_name="Random YT Channel",
        )
        result = compute_delay_probability([low_rep_delay])
        # uncorroborated -> falls into the same weighted average a rumor would:
        # negativity 0.7*0.4*0.9=0.252, *0.6=0.1512; volume 1/20*0.4=0.02 -> 17.12
        assert result == 17.12

    def test_official_delay_branch_solo_confirms_above_the_reputation_threshold(self):
        high_rep_delay = signal(
            info_type=InformationType.RELEASE_DATE_CHANGE, mentions_delay=True,
            sentiment=-0.7, confidence=0.9, reputation=0.95, source_name="Variety",
        )
        # avg_quality = 0.95 * 0.9 = 0.855 -> 85.5, no agreement bonus (one source)
        assert compute_delay_probability([high_rep_delay]) == 85.5

    def test_official_delay_branch_corroborates_across_two_low_reputation_sources(self):
        signals = [
            signal(info_type=InformationType.RELEASE_DATE_CHANGE, mentions_delay=True,
                   sentiment=-0.7, confidence=0.9, reputation=0.4, source_name="Channel A"),
            signal(info_type=InformationType.RELEASE_DATE_CHANGE, mentions_delay=True,
                   sentiment=-0.6, confidence=0.85, reputation=0.4, source_name="Channel B"),
        ]
        # avg_quality (0.4*0.9 + 0.4*0.85)/2 = 0.35, +0.05 agreement bonus -> 40.0
        assert compute_delay_probability(signals) == 40.0


class TestShiftMagnitude:
    """
    Trailers and databases routinely disagree by a couple of days (a Wednesday international
    opening vs the Friday US wide release) - that's not a delay. The size of the gap sets how
    much say a date claim gets: none within the tolerance, all of it at a month or more, and a
    linear ramp in between so nothing flips at a cliff edge.
    """

    KNOWN = date(2026, 11, 19)

    def claim(self, shift_days, reputation=1.0, source_name="Studio"):
        return signal(
            info_type=InformationType.RELEASE_DATE_CHANGE,
            extracted_release_date=self.KNOWN + timedelta(days=shift_days),
            confidence=0.9,
            mentions_delay=False,
            sentiment=1.0,
            reputation=reputation,
            source_name=source_name,
        )

    def test_a_regional_vs_wide_release_gap_is_noise(self):
        assert compute_delay_probability([self.claim(2)], known_release_date=self.KNOWN) == NO_SIGNAL_BASELINE
        assert compute_delay_probability([self.claim(-2)], known_release_date=self.KNOWN) == NO_SIGNAL_BASELINE

    def test_the_tolerance_is_inclusive(self):
        at_limit = compute_delay_probability([self.claim(MIN_MEANINGFUL_SHIFT_DAYS)], known_release_date=self.KNOWN)
        past_it = compute_delay_probability([self.claim(MIN_MEANINGFUL_SHIFT_DAYS + 1)], known_release_date=self.KNOWN)
        assert at_limit == NO_SIGNAL_BASELINE
        assert past_it > NO_SIGNAL_BASELINE

    def test_the_weight_ramps_linearly_between_the_tolerance_and_full_weight(self):
        # date score 90.0 (1.0 * 0.9), weight (days - 7) / 23, blended with the 5.0 baseline
        assert compute_delay_probability([self.claim(8)], known_release_date=self.KNOWN) == 8.7  # weight 1/23
        assert compute_delay_probability([self.claim(19)], known_release_date=self.KNOWN) == 49.35  # weight 12/23

    def test_a_month_or_more_gets_the_final_say(self):
        assert compute_delay_probability([self.claim(FULL_WEIGHT_SHIFT_DAYS)], known_release_date=self.KNOWN) == 90.0
        assert compute_delay_probability([self.claim(200)], known_release_date=self.KNOWN) == 90.0

    def test_the_same_tolerance_and_ramp_apply_to_a_move_earlier(self):
        assert compute_delay_probability([self.claim(-2)], known_release_date=self.KNOWN) == NO_SIGNAL_BASELINE
        # earlier-date score 5.0 * (1 - 0.9) = 0.5, weight 12/23, blended with the 5.0 baseline
        assert compute_delay_probability([self.claim(-19)], known_release_date=self.KNOWN) == 2.65

    def test_the_weight_is_averaged_across_a_corroborating_group(self):
        signals = [self.claim(8, reputation=0.4, source_name="A"), self.claim(30, reputation=0.4, source_name="B")]
        # date score 41.0 (0.36 + 0.05 bonus), weight (1/23 + 1) / 2, blended with the 5.0 baseline
        assert compute_delay_probability(signals, known_release_date=self.KNOWN) == 23.78

    def test_a_noise_level_claim_does_not_help_corroborate_a_real_shift(self):
        signals = [self.claim(60, reputation=0.4, source_name="A"), self.claim(2, reputation=0.4, source_name="B")]
        assert compute_delay_probability(signals, known_release_date=self.KNOWN) == NO_SIGNAL_BASELINE

    def test_a_weak_date_claim_does_not_preempt_stronger_text_evidence(self):
        """At the tolerance the date has no say, so the text score passes through untouched;
        past it the date takes over gradually instead of jumping."""
        rumor = signal()  # one maximally negative rumor -> 62.0 on its own
        assert compute_delay_probability([rumor, self.claim(7)], known_release_date=self.KNOWN) == 62.0
        assert compute_delay_probability([rumor, self.claim(8)], known_release_date=self.KNOWN) == 63.22
        assert compute_delay_probability([rumor, self.claim(30)], known_release_date=self.KNOWN) == 90.0


class TestDistanceToRelease:
    """
    The no-evidence prior depends on how long a release has left to slip: next week it has almost
    none, a year out it has plenty. Only the prior moves - what the posts actually say (a confirmed
    date move, an official delay, a credible rumor) counts the same at any distance.
    """

    RELEASE = date(2026, 11, 19)

    def on(self, days_out):
        """The day that puts RELEASE `days_out` days ahead."""
        return self.RELEASE - timedelta(days=days_out)

    def score(self, signals, days_out):
        return compute_delay_probability(signals, known_release_date=self.RELEASE, today=self.on(days_out))

    def claim(self, shift_days, reputation=1.0, source_name="Studio"):
        return signal(
            info_type=InformationType.RELEASE_DATE_CHANGE,
            extracted_release_date=self.RELEASE + timedelta(days=shift_days),
            confidence=0.9,
            mentions_delay=False,
            sentiment=1.0,
            reputation=reputation,
            source_name=source_name,
        )

    def test_an_unknown_release_date_gets_the_default_baseline(self):
        assert baseline_for(None) == NO_SIGNAL_BASELINE
        assert compute_delay_probability([]) == NO_SIGNAL_BASELINE

    def test_the_fixture_day_sits_on_the_default_baseline(self):
        """Guards the assumption every other test class leans on."""
        assert baseline_for(self.RELEASE, TODAY) == NO_SIGNAL_BASELINE

    @pytest.mark.parametrize("days_out", [0, 3, IMMINENT_DAYS, -10])
    def test_a_release_within_a_week_or_already_due_has_the_lowest_prior(self, days_out):
        assert baseline_for(self.RELEASE, self.on(days_out)) == BASELINE_IMMINENT

    @pytest.mark.parametrize("days_out", [DISTANT_DAYS, 500, 2000])
    def test_a_release_a_year_or_more_out_has_the_highest_prior(self, days_out):
        assert baseline_for(self.RELEASE, self.on(days_out)) == BASELINE_DISTANT

    def test_the_prior_ramps_linearly_between_the_two_ends(self):
        # (186 - 7) / (365 - 7) = 0.5 of the way from 1.0 to 10.0
        assert baseline_for(self.RELEASE, self.on(186)) == 5.5

    def test_the_prior_never_falls_as_the_release_gets_further_away(self):
        priors = [baseline_for(self.RELEASE, self.on(days_out)) for days_out in range(0, 400)]
        assert priors == sorted(priors)

    def test_no_signals_scores_the_prior_for_its_distance(self):
        assert self.score([], 5) == BASELINE_IMMINENT
        assert self.score([], DISTANT_DAYS) == BASELINE_DISTANT

    def test_untrusted_chatter_is_floored_at_the_prior_for_its_distance(self):
        # no negativity from a zero-reputation source, so only the 2.0 volume term is left
        untrusted = signal(sentiment=-0.5, reputation=0.0, confidence=0.9)
        assert self.score([untrusted], 5) == 2.0  # already above a next-week prior
        assert self.score([untrusted], DISTANT_DAYS) == BASELINE_DISTANT  # lifted to a year-out prior

    def test_a_credible_rumor_counts_the_same_at_any_distance(self):
        assert self.score([signal()], 5) == 62.0
        assert self.score([signal()], DISTANT_DAYS) == 62.0

    def test_a_confirmed_date_move_counts_the_same_at_any_distance(self):
        # solo source: 1.0 * 0.9 = 90.0, full weight for a 102-day shift
        assert self.score([self.claim(102)], 5) == 90.0
        assert self.score([self.claim(102)], DISTANT_DAYS) == 90.0

    def test_a_confirmed_move_earlier_pulls_below_the_prior_for_its_distance(self):
        # prior * (1 - 0.9 strength)
        assert self.score([self.claim(-102)], 5) == 0.1
        assert self.score([self.claim(-102)], DISTANT_DAYS) == 1.0

    def test_evidence_of_a_delay_never_scores_below_the_prior(self):
        """Two zero-reputation sources only clear corroboration on the 0.05 agreement bonus (5.0) -
        fine for a next-week release, but under the prior of a year-out one."""
        agreeing = [self.claim(60, reputation=0.0, source_name="A"), self.claim(60, reputation=0.0, source_name="B")]
        assert self.score(agreeing, 5) == 5.0
        assert self.score(agreeing, DISTANT_DAYS) == BASELINE_DISTANT

        official = [
            signal(info_type=InformationType.RELEASE_DATE_CHANGE, mentions_delay=True, sentiment=-0.7,
                   confidence=0.9, reputation=0.0, source_name=name)
            for name in ("A", "B")
        ]
        assert self.score(official, 5) == 5.0
        assert self.score(official, DISTANT_DAYS) == BASELINE_DISTANT

    def test_today_defaults_to_the_real_clock(self):
        assert _compute_delay_probability([], date.today() + timedelta(days=5)) == BASELINE_IMMINENT
        assert _compute_delay_probability([], date.today() + timedelta(days=400)) == BASELINE_DISTANT


class TestNoRelevantSignals:
    """An absence of delay-relevant signal is not proof of an on-time release - it lands on a
    small non-zero baseline rather than a flat, falsely confident 0.0."""

    def test_empty_input(self):
        assert compute_delay_probability([]) == NO_SIGNAL_BASELINE

    def test_unrelated_posts_are_ignored(self):
        signals = [signal(info_type=InformationType.UNRELATED) for _ in range(10)]
        assert compute_delay_probability(signals) == NO_SIGNAL_BASELINE

    def test_rumors_that_do_not_mention_a_delay_are_ignored(self):
        signals = [signal(mentions_delay=False) for _ in range(10)]
        assert compute_delay_probability(signals) == NO_SIGNAL_BASELINE

    def test_release_date_change_without_delay_flag_is_not_counted_as_a_rumor(self):
        signals = [
            signal(info_type=InformationType.RELEASE_DATE_CHANGE, mentions_delay=False)
        ]
        assert compute_delay_probability(signals) == NO_SIGNAL_BASELINE


class TestRumorScoring:
    def test_single_maximally_negative_rumor(self):
        # negativity 1.0 * weight 0.6, volume 1/20 = 0.05 * weight 0.4 -> 0.62
        assert compute_delay_probability([signal()]) == 62.0

    def test_low_reputation_and_low_confidence_dampen_the_score(self):
        # 0.5 * 0.8 * 0.5 = 0.2 negativity, volume 0.05 -> 0.6 * 0.2 + 0.4 * 0.05 = 0.14
        result = compute_delay_probability(
            [signal(sentiment=-0.5, reputation=0.8, confidence=0.5)]
        )
        assert result == 14.0

    def test_positive_sentiment_contributes_no_negativity(self):
        # only the volume term survives (0.4 * 1/20 = 2.0), then the baseline floor lifts it
        assert compute_delay_probability([signal(sentiment=0.9)]) == NO_SIGNAL_BASELINE

    def test_untrusted_delay_chatter_never_scores_below_no_chatter(self):
        """A zero-reputation source contributes no negativity, leaving only the tiny volume
        term - which used to put a delay mention *below* the no-signal baseline."""
        untrusted = signal(sentiment=-0.5, reputation=0.0, confidence=0.9)
        assert compute_delay_probability([untrusted]) == NO_SIGNAL_BASELINE

    def test_the_floor_does_not_clip_real_scores(self):
        assert compute_delay_probability([signal()]) == 62.0

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
        # raw 0.6 * (2/3 * 6/7) + 0.4 * 1/20 = 0.362857... - well above the baseline floor
        result = compute_delay_probability([signal(sentiment=-2 / 3, confidence=6 / 7)])
        assert result == 36.29

    def test_unrelated_signals_do_not_dilute_the_average(self):
        """Only rumor-with-delay signals form the denominator."""
        with_noise = [signal()] + [signal(info_type=InformationType.UNRELATED) for _ in range(19)]
        assert compute_delay_probability(with_noise) == compute_delay_probability([signal()])
