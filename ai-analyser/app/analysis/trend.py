from app.db.models import RumorTrendSnapshot

# A single pipeline run is a snapshot, not a verdict - a delay signal that keeps climbing
# run over run is a much stronger indicator than one high-but-isolated reading. Needs at
# least this many recent snapshots before a trend is called at all.
MIN_SNAPSHOTS_FOR_TREND = 3
MEANINGFUL_MOVE_POINTS = 10.0


def record_snapshot(session, media_item_id, delay_probability: float, signal_count: int) -> None:
    session.add(RumorTrendSnapshot(
        media_item_id=media_item_id,
        delay_probability=delay_probability,
        signal_count=signal_count,
    ))
    session.commit()


def compute_trend(session, media_item_id, lookback: int = 5) -> str | None:
    """
    Looks at the last `lookback` snapshots for this media item (oldest to newest) and
    classifies the movement of delay_probability as "rising", "falling", or "stable".
    Returns None when there isn't enough history yet to say anything meaningful.
    """
    snapshots = (
        session.query(RumorTrendSnapshot)
        .filter(RumorTrendSnapshot.media_item_id == media_item_id)
        .order_by(RumorTrendSnapshot.computed_at.desc())
        .limit(lookback)
        .all()
    )

    if len(snapshots) < MIN_SNAPSHOTS_FOR_TREND:
        return None

    values = [float(s.delay_probability) for s in reversed(snapshots)]

    is_non_decreasing = all(b >= a for a, b in zip(values, values[1:]))
    if is_non_decreasing and values[-1] - values[0] >= MEANINGFUL_MOVE_POINTS:
        return "rising"

    is_non_increasing = all(b <= a for a, b in zip(values, values[1:]))
    if is_non_increasing and values[0] - values[-1] >= MEANINGFUL_MOVE_POINTS:
        return "falling"

    return "stable"
