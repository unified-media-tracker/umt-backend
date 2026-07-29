import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Numeric, DateTime, Boolean, Enum
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
    published_at = Column(DateTime, nullable=False)
    ingested_at = Column(DateTime(timezone=True), nullable=False, default=datetime.now(timezone.utc))


class AnalysisReport(Base):
    __tablename__ = "analysis_report"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    media_item_id = Column(UUID(as_uuid=True), nullable=False, index=True)
    summary = Column(String, nullable=False)
    final_delay_probability = Column(Numeric, nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False, default=datetime.now(timezone.utc))
