from enum import Enum

from pydantic import BaseModel, ConfigDict, Field, field_validator


class ApiModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
        extra="forbid",
    )


class Role(str, Enum):
    BACKEND = "BACKEND"
    FRONTEND = "FRONTEND"
    FULLSTACK = "FULLSTACK"
    PLATFORM = "PLATFORM"
    AI = "AI"
    ETC = "ETC"


class Competency(ApiModel):
    id: int = Field(gt=0)
    name: str = Field(min_length=1, max_length=100)
    category: str = Field(min_length=1, max_length=30)
    aliases: list[str] = Field(default_factory=list, max_length=100)


class ExtractRequest(ApiModel):
    posting_text: str = Field(alias="postingText", min_length=1, max_length=100_000)
    competencies: list[Competency] = Field(min_length=1, max_length=500)

    @field_validator("posting_text")
    @classmethod
    def reject_blank_posting(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("공고 원문이 비어 있습니다.")
        return value

    @field_validator("competencies")
    @classmethod
    def reject_duplicate_ids(cls, value: list[Competency]) -> list[Competency]:
        ids = [item.id for item in value]
        if len(ids) != len(set(ids)):
            raise ValueError("역량 ID는 중복될 수 없습니다.")
        return value


class RequiredCompetency(ApiModel):
    competency_id: int = Field(alias="competencyId", gt=0)
    weight: float = Field(ge=0.5, le=1.0)
    evidence: str = Field(min_length=1)


class ModelExtractResult(ApiModel):
    required: list[RequiredCompetency]
    new_competencies: list[str] = Field(alias="newCompetencies")
    role: Role


class ExtractMeta(ApiModel):
    model: str
    latency_ms: int = Field(alias="latencyMs", ge=0)
    input_tokens: int | None = Field(alias="inputTokens", default=None, ge=0)
    output_tokens: int | None = Field(alias="outputTokens", default=None, ge=0)
    dropped_invalid_results: int = Field(alias="droppedInvalidResults", ge=0)


class ExtractResponse(ModelExtractResult):
    meta: ExtractMeta = Field(alias="_meta")
