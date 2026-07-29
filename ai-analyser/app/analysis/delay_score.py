from app.schemas import InformationType, RumorSignalInput

SENTIMENT_WEIGHT = 0.6
VOLUME_WEIGHT = 0.4
VOLUME_SATURATION_POINT = 20


def compute_delay_probability(signals: list[RumorSignalInput]) -> float:
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
