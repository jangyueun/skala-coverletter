from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.extract import router as extract_router
from app.api.provider import router as provider_router
from app.services.mock_provider import MockAiProvider
from app.services.provider import ProviderError


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.ai_provider = MockAiProvider()
    app.state.extraction_client = None
    try:
        yield
    finally:
        if app.state.extraction_client is not None:
            await app.state.extraction_client.close()


app = FastAPI(
    title="CareerFit AI Server",
    version="0.1.0",
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
    return {"status": "ok"}
