from datetime import date

from app.schemas import InformationType, RumorSignalInput

SENTIMENT_WEIGHT = 0.6
VOLUME_WEIGHT = 0.4
VOLUME_SATURATION_POINT = 20
CONFIRMED_DATE_CONFIDENCE_THRESHOLD = 0.8


def compute_delay_probability(signals: list[RumorSignalInput], known_release_date: date | None = None) -> float:
    # A post that explicitly states a date, classified as an official release_date_change,
    # is a stronger signal than sentiment-based rumor scoring below - but only once compared
    # against what we already knew.
    if known_release_date is not None:
        confirmed_changes = [
            s for s in signals
            if s.info_type == InformationType.RELEASE_DATE_CHANGE
               and s.extracted_release_date is not None
               and s.evaluation_confidence > CONFIRMED_DATE_CONFIDENCE_THRESHOLD
               and s.extracted_release_date != known_release_date
        ]
        if any(s.extracted_release_date > known_release_date for s in confirmed_changes):
            return 100.0
        if any(s.extracted_release_date < known_release_date for s in confirmed_changes):
            return 0.0  # a confirmed move to an earlier date is the opposite of a delay

    official_delays = [
        s for s in signals
        if s.info_type == InformationType.RELEASE_DATE_CHANGE
           and s.mentions_delay
           and s.sentiment_score < 0
           and s.evaluation_confidence > 0.8
    ]
    if official_delays:
        return 100.0

    relevant = [
        s for s in signals
        if s.mentions_delay and s.info_type == InformationType.RUMOR
    ]
    if not relevant:
        return 0.0

    weighted_negativity = sum(
        max(0.0, -s.sentiment_score) * s.source_reputation_score * s.evaluation_confidence
        for s in relevant
    ) / len(relevant)

    volume_factor = min(len(relevant) / VOLUME_SATURATION_POINT, 1.0)

    raw_score = SENTIMENT_WEIGHT * weighted_negativity + VOLUME_WEIGHT * volume_factor
    return round(min(raw_score, 1.0) * 100, 2)
