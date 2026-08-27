from datetime import date

from app.db.models import DelayProbabilityHistory

# A single pipeline run isn't a verdict - a delay signal climbing run over run is much
# stronger evidence than one isolated high reading. Needs at least this many recent
# history rows before a trend is called at all.
MIN_HISTORY_FOR_TREND = 3
MEANINGFUL_MOVE_POINTS = 10.0


def record_delay_probability(
        session, media_item_id, delay_probability: float, signal_count: int,
        known_release_date: date | None = None,
) -> None:
    session.add(DelayProbabilityHistory(
        media_item_id=media_item_id,
        delay_probability=delay_probability,
        signal_count=signal_count,
        known_release_date=known_release_date,
    ))
    session.commit()


def compute_trend(session, media_item_id, lookback: int = 5) -> str | None:
    """
    Looks at the last `lookback` history rows for this media item (oldest to newest) and
    classifies the movement of delay_probability as "RISING", "FALLING", or "STABLE".
    Returns None when there isn't enough history yet to say anything meaningful.
    """
    history = (
        session.query(DelayProbabilityHistory)
        .filter(DelayProbabilityHistory.media_item_id == media_item_id)
        .order_by(DelayProbabilityHistory.computed_at.desc())
        .limit(lookback)
        .all()
    )

    if len(history) < MIN_HISTORY_FOR_TREND:
        return None

    values = [float(s.delay_probability) for s in reversed(history)]

    is_non_decreasing = all(b >= a for a, b in zip(values, values[1:]))
    if is_non_decreasing and values[-1] - values[0] >= MEANINGFUL_MOVE_POINTS:
        return "RISING"

    is_non_increasing = all(b <= a for a, b in zip(values, values[1:]))
    if is_non_increasing and values[0] - values[-1] >= MEANINGFUL_MOVE_POINTS:
        return "FALLING"

    return "STABLE"
