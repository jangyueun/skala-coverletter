"""HTTP/DB/작업 상태를 모르는 제공자 인터페이스. 재시도는 Spring이 한다."""

from typing import Protocol

from app.schemas.provider import (
    DraftRequest, DraftResponse, IntakeRequest, IntakeResponse,
    MatchRequest, MatchResponse, PostingAnalysisRequest, PostingAnalysisResponse,
    PromptVersions,
)


class ProviderError(Exception):
    """일시적 제공자 장애. 외부 메시지는 API 레이어의 고정 문구만 사용한다."""


class AiProvider(Protocol):
    def versions(self) -> PromptVersions: ...

    async def posting_analysis(self, request: PostingAnalysisRequest) -> PostingAnalysisResponse: ...

    async def experience_intake(self, request: IntakeRequest) -> IntakeResponse: ...

    async def match(self, request: MatchRequest) -> MatchResponse: ...

    async def draft(self, request: DraftRequest) -> DraftResponse: ...
