import asyncio
import logging
from collections.abc import Awaitable, Callable
from typing import TypeVar

from fastapi import APIRouter, Depends, Request
from pydantic import ValidationError

from app.api.extract import require_internal_token
from app.core.config import settings
from app.schemas.provider import (
    DraftRequest, DraftResponse, ErrorResponse, IntakeRequest, IntakeResponse,
    MatchRequest, MatchResponse, PostingAnalysisRequest, PostingAnalysisResponse,
    PromptVersions, ProviderResponse,
)
from app.services.provider import AiProvider, ProviderError

logger = logging.getLogger(__name__)
router = APIRouter(
    prefix="/ai", tags=["AI provider"], dependencies=[Depends(require_internal_token)],
    responses={422: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
Result = TypeVar("Result", bound=ProviderResponse)


def get_provider(request: Request) -> AiProvider:
    return request.app.state.ai_provider


async def invoke(call: Callable[[], Awaitable[Result]], schema: type[Result]) -> Result:
    try:
        async with asyncio.timeout(settings.request_timeout_seconds):
            result = await call()
        return schema.model_validate(result)
    except (ProviderError, TimeoutError, ValidationError) as error:
        # 외부 응답/키/URL을 로그나 HTTP 오류에 포함하지 않는다.
        logger.warning("AI provider failed: %s", type(error).__name__)
        raise ProviderError from None


@router.get("/prompts/versions", response_model=PromptVersions)
async def versions(provider: AiProvider = Depends(get_provider)) -> PromptVersions:
    return provider.versions()


@router.post("/posting-analysis", response_model=PostingAnalysisResponse)
async def posting_analysis(body: PostingAnalysisRequest, provider: AiProvider = Depends(get_provider)):
    return await invoke(lambda: provider.posting_analysis(body), PostingAnalysisResponse)


@router.post("/experience-intake", response_model=IntakeResponse)
async def experience_intake(body: IntakeRequest, provider: AiProvider = Depends(get_provider)):
    return await invoke(lambda: provider.experience_intake(body), IntakeResponse)


@router.post("/match", response_model=MatchResponse)
async def match(body: MatchRequest, provider: AiProvider = Depends(get_provider)):
    return await invoke(lambda: provider.match(body), MatchResponse)


@router.post("/draft", response_model=DraftResponse)
async def draft(body: DraftRequest, provider: AiProvider = Depends(get_provider)):
    return await invoke(lambda: provider.draft(body), DraftResponse)
