import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Numeric, DateTime, Date, Boolean, Enum
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import declarative_base

from app.schemas import InformationType

Base = declarative_base()


class RumorSignal(Base):
    __tablename__ = "rumor_signal"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    media_item_id = Column(UUID(as_uuid=True), nullable=False, index=True)
    source_name = Column(String(100), nullable=False)
    source_url = Column(String, nullable=False)
    source_reputation_score = Column(Numeric, nullable=False)
    sentiment_score = Column(Numeric, nullable=False)
    mentions_delay = Column(Boolean, nullable=False)
    info_type = Column(Enum(InformationType, name="information_type_enum"), nullable=False)
    evaluation_confidence = Column(Numeric, nullable=False)
    extracted_release_date = Column(Date, nullable=True)
    published_at = Column(DateTime, nullable=False)
    ingested_at = Column(DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc))

class SourceReputation(Base):
    __tablename__ = "source_reputation"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    source_name = Column(String(100), nullable=False)
    reputation_score = Column(Numeric, nullable=False)
    is_curated = Column(Boolean, nullable=False, default=False)
    reasoning = Column(String, nullable=True)
    created_at = Column(DateTime, nullable=False, default=lambda: datetime.now(timezone.utc))
    updated_at = Column(DateTime, nullable=False,default=lambda: datetime.now(timezone.utc))

class DelayProbabilityHistory(Base):
    __tablename__ = "delay_probability_history"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    media_item_id = Column(UUID(as_uuid=True), nullable=False, index=True)
    delay_probability = Column(Numeric, nullable=False)
    signal_count = Column(Numeric, nullable=False)
    # What we believed the release date was at this snapshot - the future training label
    # compares MediaOutcome.actual_release_date against this, not against today's date.
    known_release_date = Column(Date, nullable=True)
    computed_at = Column(DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc))


class MediaOutcome(Base):
    """Ground truth: when a media item actually released. One row per item ever -
    media_item_id is the primary key, so a duplicate delivery just re-hits the same row."""
    __tablename__ = "media_outcome"

    media_item_id = Column(UUID(as_uuid=True), primary_key=True)
    actual_release_date = Column(Date, nullable=False)
    resolved_at = Column(DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc))