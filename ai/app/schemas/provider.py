"""docs/api-spec-v6.md §8의 Spring ↔ Python JSON 계약."""

from datetime import date
from typing import Annotated, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, HttpUrl, model_validator
from pydantic.alias_generators import to_camel

Id = Annotated[int, Field(strict=True, gt=0)]
Text = Annotated[str, Field(min_length=1, max_length=100_000, pattern=r"\S")]
Unit = Annotated[float, Field(ge=0, le=1, allow_inf_nan=False)]
Category = Literal[
    "TEAM_PROJECT", "PERSONAL_PROJECT", "PRACTICE_PROJECT",
    "EXTERNAL_ACTIVITY", "EMPLOYMENT", "AWARD_CERTIFICATE",
]


class Contract(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel, populate_by_name=True, extra="forbid",
    )


def unique(values: list[int]) -> None:
    if len(values) != len(set(values)):
        raise ValueError("중복된 ID는 허용하지 않습니다.")


class Competency(Contract):
    id: Id
    name: Text
    category: Literal["ROLE", "TECH", "SOFT", "DOMAIN", "VALUE"]
    aliases: list[Text] = Field(default_factory=list, max_length=100)


class WithDictionary(Contract):
    competencies: list[Competency] = Field(max_length=500)

    @model_validator(mode="after")
    def check_dictionary(self) -> Self:
        unique([c.id for c in self.competencies])
        return self


class PostingAnalysisRequest(WithDictionary):
    posting_id: Id
    content: Text


class RequiredCompetency(Contract):
    competency_id: Id
    weight: Unit
    evidence: Text


class ProviderResponse(Contract):
    prompt_version: Text
    model: Text


class PostingAnalysisResponse(ProviderResponse):
    required: list[RequiredCompetency]


class Period(Contract):
    start_date: date | None = None
    end_date: date | None = None

    @model_validator(mode="after")
    def check_period(self) -> Self:
        if self.start_date and self.end_date and self.end_date < self.start_date:
            raise ValueError("종료일은 시작일보다 빠를 수 없습니다.")
        return self


class ExistingExperience(Period):
    id: Id
    title: Text
    category: Category


class IntakeRequest(WithDictionary):
    links: list[HttpUrl] = Field(max_length=50)
    file_urls: list[HttpUrl] = Field(max_length=5)
    existing_experiences: list[ExistingExperience] = Field(max_length=500)

    @model_validator(mode="after")
    def check_sources(self) -> Self:
        if not self.links and not self.file_urls:
            raise ValueError("링크나 파일 URL이 필요합니다.")
        unique([e.id for e in self.existing_experiences])
        return self


class IntakeQuestion(Contract):
    field: Literal["situation", "task", "action", "result"]
    q: Text
    why: Text


class Candidate(Period):
    key: Text
    title: Text
    category: Category
    situation: str
    action: str
    questions: list[IntakeQuestion]
    suggested_competency_ids: list[Id]
    duplicate_of_experience_id: Id | None


class IntakeResponse(ProviderResponse):
    candidates: list[Candidate]


class MatchRequirement(Contract):
    competency_id: Id
    weight: Unit
    evidence_line: str


class MatchPosting(Contract):
    id: Id
    required: list[MatchRequirement] = Field(max_length=500)

    @model_validator(mode="after")
    def check_ids(self) -> Self:
        unique([r.competency_id for r in self.required])
        return self


class ExperienceStrength(Contract):
    competency_id: Id
    strength: Unit


class MatchExperience(Contract):
    id: Id
    title: Text
    result: str
    competencies: list[ExperienceStrength] = Field(max_length=500)

    @model_validator(mode="after")
    def check_ids(self) -> Self:
        unique([c.competency_id for c in self.competencies])
        return self


class MatchRequest(Contract):
    posting: MatchPosting
    experiences: list[MatchExperience] = Field(max_length=500)

    @model_validator(mode="after")
    def check_ids(self) -> Self:
        unique([e.id for e in self.experiences])
        return self


class MatchRow(Contract):
    competency_id: Id
    weight: Unit
    score: Unit
    is_gap: bool
    experience_ids: list[Id]


class MatchResponse(ProviderResponse):
    overall: Unit
    verdict: Literal["RECOMMEND", "CONDITIONAL", "HOLD"]
    rows: list[MatchRow]


class DraftQuestion(Contract):
    prompt_text: Text
    length_limit: Annotated[int, Field(strict=True, gt=0, le=100_000)] | None


class DraftRequirement(Contract):
    name: Text
    weight: Unit
    evidence_line: str = ""


class DraftPosting(Contract):
    company: Text
    position: Text
    # 공고 원문 전문. 초안이 담당 업무·자격요건·인재상 문장에 경험을 맞대려면 이름만으로는 부족하다. 없으면 빈 문자열.
    content: str = ""
    required: list[DraftRequirement] = Field(default_factory=list, max_length=500)


class DraftExperience(Contract):
    title: Text
    situation: str
    task: str
    action: str
    result: str


class DraftRequest(Contract):
    question: DraftQuestion
    posting: DraftPosting
    experiences: list[DraftExperience] = Field(max_length=500)


class DraftResponse(ProviderResponse):
    draft: str
    char_count: int = Field(ge=0)

    @model_validator(mode="after")
    def check_count(self) -> Self:
        if self.char_count != len(self.draft):
            raise ValueError("charCount는 실제 글자 수여야 합니다.")
        return self


class PromptVersions(BaseModel):
    # 이 엔드포인트만 명세에 따라 snake_case다.
    model_config = ConfigDict(extra="forbid", frozen=True)
    posting_analysis: str = "v2"
    experience_intake: str = "v1"
    match: str = "v1"
    draft: str = "v1"


class ErrorResponse(Contract):
    code: str
    message: str
