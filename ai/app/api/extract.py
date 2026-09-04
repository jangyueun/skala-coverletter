from hmac import compare_digest

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status

from app.core.config import settings
from app.schemas.extract import ExtractRequest, ExtractResponse
from app.services.extractor import ExtractionClient, extract_posting

router = APIRouter(prefix="/internal/ai", tags=["AI"])


def require_internal_token(
    authorization: str | None = Header(default=None),
) -> None:
    if not settings.internal_token:
        return
    if not compare_digest((authorization or "").encode(), f"Bearer {settings.internal_token}".encode()):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="유효하지 않은 내부 서버 인증 정보입니다.",
        )


def get_extraction_client(request: Request) -> ExtractionClient:
    # 기존 경로를 실제 호출할 때만 SDK를 준비한다. 새 Mock API는 Claude에 의존하지 않는다.
    if request.app.state.extraction_client is None:
        from app.services.anthropic_client import AnthropicExtractionClient

        request.app.state.extraction_client = AnthropicExtractionClient(settings)
    return request.app.state.extraction_client


@router.post(
    "/extract",
    response_model=ExtractResponse,
    dependencies=[Depends(require_internal_token)],
    deprecated=True,
)
async def extract(
    body: ExtractRequest,
    client: ExtractionClient = Depends(get_extraction_client),
) -> ExtractResponse:
    return await extract_posting(body, client)
