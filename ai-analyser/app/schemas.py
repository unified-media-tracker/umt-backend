from enum import Enum
from pydantic import BaseModel, Field, field_validator
from dataclasses import dataclass


# ============================================================
# ENUMS
# ============================================================

class InformationType(str, Enum):
    RELEASE_DATE_CHANGE = "release_date_change"
    RUMOR = "rumor"
    UNRELATED = "unrelated"


# ============================================================
# PYDANTIC SCHEMAS
# ============================================================

class PostEvaluation(BaseModel):
    reasoning: str = Field(
        description="Step-by-step explanation of your thought process before classifying the post."
    )
    sentiment_score: float = Field(
        ge=-1.0,
        le=1.0,
        description="Sentiment regarding the delay (-1.0 means highly likely delayed, 1.0 means highly likely releasing early or on time)."
    )
    mentions_delay: bool = Field(
        description="True if the post explicitly mentions a delay or release date pushback."
    )
    info_type: InformationType = Field(
        description="Classification of the information."
    )
    confidence: float = Field(
        ge=0.0,
        le=1.0,
        description="Your confidence in this evaluation."
    )

    @field_validator('confidence', mode='before')
    @classmethod
    def normalize_confidence(cls, v):
        if isinstance(v, (int, float)) and v > 1.0:
            if v > 10.0:
                return float(v) / 100.0
            else:
                return float(v) / 10.0
        return v


class SourceReputationEvaluation(BaseModel):
    reasoning: str = Field(
        description="Step-by-step explanation of your thought process for scoring the source's reliability."
    )
    reputation_score: float = Field(
        ge=0.0,
        le=1.0,
        description="The reputation score of the source. (e.g., 0.9 for highly reputable, 0.1 for untrustworthy)."
    )


# ============================================================
# DATACLASSES
# ============================================================

@dataclass
class RumorSignalInput:
    sentiment_score: float
    source_reputation_score: float
    mentions_delay: bool
    evaluation_confidence: float
    info_type: InformationType
