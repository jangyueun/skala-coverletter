import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.extract import router as extract_router
from app.api.provider import router as provider_router
from app.core.config import settings
from app.services.mock_provider import MockAiProvider
from app.services.provider import ProviderError

# 앱 로거(app.*)의 INFO 를 콘솔로 보낸다. uvicorn 은 자기 로거만 꾸미고 루트에는 핸들러를 안 달아서, 이게 없으면
# 토큰·캐시·advisor 호출·후보 수 같은 INFO 로그가 전부 사라진다. basicConfig 는 루트에 핸들러가 없을 때만 붙는다.
logging.basicConfig(level=logging.INFO, format="%(levelname)s:     [%(name)s] %(message)s")
logger = logging.getLogger("uvicorn.error")


def build_provider():
    """키가 있으면 Claude, 없으면 Mock. 키 유무 하나로 갈린다 — 팀원이 키 없이 띄워도 계약은 그대로 돈다.
    AI_FORCE_MOCK=1 이면 키가 있어도 Mock — 테스트가 실제 API 를 부르지 않게 하는 안전장치다."""
    if settings.anthropic_api_key and not settings.force_mock:
        # 여기서 import 하는 이유: Mock 경로는 SDK 를 건드리지 않아야 한다(tests/test_provider_contract.py 가 지킨다).
        from app.services.claude_provider import ClaudeAiProvider

        logger.info(
            "AI provider: Claude (공고 분석 %s/%s · 인테이크 %s/%s · 초안 %s/%s)",
            settings.model_posting_analysis, settings.effort_posting_analysis,
            settings.model_experience_intake, settings.effort_experience_intake,
            settings.model_draft, settings.effort_draft,
        )
        return ClaudeAiProvider(settings)
    logger.warning("AI provider: Mock — ANTHROPIC_API_KEY 가 없어 실제 AI 를 부르지 않습니다")
    return MockAiProvider()


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.ai_provider = build_provider()
    app.state.extraction_client = None
    try:
        yield
    finally:
        if app.state.extraction_client is not None:
            await app.state.extraction_client.close()
        close = getattr(app.state.ai_provider, "close", None)
        if close is not None:
            await close()


app = FastAPI(
    title="Career Lab AI Server",
    version="0.2.0",
    lifespan=lifespan,
)
app.include_router(extract_router)
app.include_router(provider_router)


@app.exception_handler(ProviderError)
async def provider_error(request: Request, error: ProviderError) -> JSONResponse:
    return JSONResponse(status_code=503, content={
        "code": "AI_PROVIDER_ERROR", "message": "AI 제공자를 사용할 수 없습니다. 잠시 후 재시도해 주세요.",
    })


@app.exception_handler(RequestValidationError)
async def validation_error(request: Request, error: RequestValidationError) -> JSONResponse:
    # FastAPI 기본 상세 오류는 입력 원문/서명된 URL을 포함할 수 있다.
    return JSONResponse(status_code=422, content={
        "code": "VALIDATION_FAILED", "message": "요청 데이터가 AI 제공자 계약에 맞지 않습니다.",
    })


@app.get("/health")
async def health() -> dict[str, str]:
    # 어느 제공자인지는 시작 로그("AI provider: ...")에 남는다. 응답 모양은 계약대로 고정한다.
    return {"status": "ok"}
