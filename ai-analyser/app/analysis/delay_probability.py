from datetime import date

from app.schemas import InformationType, RumorSignalInput

SENTIMENT_WEIGHT = 0.6
VOLUME_WEIGHT = 0.4
VOLUME_SATURATION_POINT = 20
CONFIRMED_DATE_CONFIDENCE_THRESHOLD = 0.8
SOLO_CONFIRMATION_REPUTATION_THRESHOLD = 0.8  # a source this reputable can confirm alone
MIN_CORROBORATING_SOURCES = 2  # otherwise, this many independent sources must agree
AGREEMENT_BONUS_PER_EXTRA_SOURCE = 0.05
MAX_AGREEMENT_BONUS = 0.15
MAX_CONFIRMATION_STRENGTH = 0.99  # never mathematically certain, however strong the evidence
NO_SIGNAL_BASELINE = 5.0  # the prior when the release date - hence its distance - is unknown; not a real statistic
BASELINE_IMMINENT = 1.0  # prior to a release IMMINENT_DAYS out or less: almost no room left to slip
BASELINE_DISTANT = 10.0  # prior to a release DISTANT_DAYS out or more: plenty of time to move
IMMINENT_DAYS = 7
DISTANT_DAYS = 365
MIN_MEANINGFUL_SHIFT_DAYS = 7  # a smaller gap is usually a regional-vs-wide release quirk, not a delay
FULL_WEIGHT_SHIFT_DAYS = 30  # a shift this big gets the final say over the text-based score


def baseline_for(known_release_date: date | None, today: date | None = None) -> float:
    """
    The prior for "no delay-relevant signal at all": a release next week has almost no room left
    to slip, one a year out has plenty. Linear between the two ends; an unknown date, so an
    unknown distance, gets the NO_SIGNAL_BASELINE default.
    """
    if known_release_date is None:
        return NO_SIGNAL_BASELINE
    days_out = (known_release_date - (today or date.today())).days
    ramp = (days_out - IMMINENT_DAYS) / (DISTANT_DAYS - IMMINENT_DAYS)
    return round(BASELINE_IMMINENT + (BASELINE_DISTANT - BASELINE_IMMINENT) * min(max(ramp, 0.0), 1.0), 2)


def _confirmation_strength(signals: list[RumorSignalInput]) -> float | None:
    """
    None if the group doesn't clear the corroboration bar - a single low-reputation post
    shouldn't be able to dominate the result.
    """
    if not signals:
        return None
    source_count = len({s.source_name for s in signals})
    solo_confirms = any(s.source_reputation_score >= SOLO_CONFIRMATION_REPUTATION_THRESHOLD for s in signals)
    if not solo_confirms and source_count < MIN_CORROBORATING_SOURCES:
        return None

    avg_quality = sum(s.source_reputation_score * s.evaluation_confidence for s in signals) / len(signals)
    agreement_bonus = min((source_count - 1) * AGREEMENT_BONUS_PER_EXTRA_SOURCE, MAX_AGREEMENT_BONUS)
    return min(avg_quality + agreement_bonus, MAX_CONFIRMATION_STRENGTH)


def _shift_weight(shift_days: int) -> float:
    """0 up to the noise tolerance, then a linear ramp to 1 at FULL_WEIGHT_SHIFT_DAYS."""
    ramp = (abs(shift_days) - MIN_MEANINGFUL_SHIFT_DAYS) / (FULL_WEIGHT_SHIFT_DAYS - MIN_MEANINGFUL_SHIFT_DAYS)
    return min(max(ramp, 0.0), 1.0)


def _date_evidence_weight(signals: list[RumorSignalInput], known_release_date: date) -> float:
    """How much say a group of date claims gets over the text-based score, averaged over the group."""
    weights = [_shift_weight((s.extracted_release_date - known_release_date).days) for s in signals]
    return sum(weights) / len(weights)


def _text_score(signals: list[RumorSignalInput], baseline: float) -> float:
    """Score from what the posts say (official delays, rumors), ignoring any extracted dates."""
    official_delays = [
        s for s in signals
        if s.info_type == InformationType.RELEASE_DATE_CHANGE
           and s.mentions_delay
           and s.sentiment_score < 0
           and s.evaluation_confidence > 0.8
    ]
    delay_strength = _confirmation_strength(official_delays)
    if delay_strength is not None:
        return round(max(delay_strength * 100, baseline), 2)

    # An official_delays candidate that didn't clear corroboration above is not proof, but it's
    # still evidence - let it into the weighted average below rather than vanishing, which would
    # just be the opposite hard extreme.
    relevant = [
        s for s in signals
        if (s.mentions_delay and s.info_type == InformationType.RUMOR) or s in official_delays
    ]
    if not relevant:
        return baseline

    weighted_negativity = sum(
        max(0.0, -s.sentiment_score) * s.source_reputation_score * s.evaluation_confidence
        for s in relevant
    ) / len(relevant)

    volume_factor = min(len(relevant) / VOLUME_SATURATION_POINT, 1.0)

    raw_score = SENTIMENT_WEIGHT * weighted_negativity + VOLUME_WEIGHT * volume_factor
    # Chatter about a delay, however untrusted, is never better news than no chatter at all.
    return round(max(min(raw_score, 1.0) * 100, baseline), 2)


def _blend(date_score: float, date_weight: float, text_score: float) -> float:
    return round(date_weight * date_score + (1 - date_weight) * text_score, 2)


def compute_delay_probability(
        signals: list[RumorSignalInput], known_release_date: date | None = None, today: date | None = None,
) -> float:
    baseline = baseline_for(known_release_date, today)
    text_score = _text_score(signals, baseline)
    if known_release_date is None:
        return text_score

    # A post that explicitly states a date, classified as an official release_date_change,
    # is a stronger signal than the text-based score - but only once compared against what we
    # already knew, and only for a gap big enough to be a real move. The gap sets how much say
    # the date evidence gets, so a Wed-vs-Fri quirk counts for nothing and a month-plus slip for all.
    confirmed_changes = [
        s for s in signals
        if s.info_type == InformationType.RELEASE_DATE_CHANGE
           and s.extracted_release_date is not None
           and s.evaluation_confidence > CONFIRMED_DATE_CONFIDENCE_THRESHOLD
           and abs((s.extracted_release_date - known_release_date).days) > MIN_MEANINGFUL_SHIFT_DAYS
    ]
    later = [s for s in confirmed_changes if s.extracted_release_date > known_release_date]
    earlier = [s for s in confirmed_changes if s.extracted_release_date < known_release_date]

    later_strength = _confirmation_strength(later)
    if later_strength is not None:
        return _blend(
            max(later_strength * 100, baseline), _date_evidence_weight(later, known_release_date), text_score,
        )

    earlier_strength = _confirmation_strength(earlier)
    if earlier_strength is not None:
        # a confirmed move earlier is the opposite of a delay: it pulls below the baseline, never above
        return _blend(
            baseline * (1 - earlier_strength),
            _date_evidence_weight(earlier, known_release_date), text_score,
        )

    return text_score
