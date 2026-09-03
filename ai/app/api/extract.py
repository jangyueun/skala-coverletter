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
    if authorization != f"Bearer {settings.internal_token}":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="유효하지 않은 내부 서버 인증 정보입니다.",
        )


def get_extraction_client(request: Request) -> ExtractionClient:
    return request.app.state.extraction_client


@router.post(
    "/extract",
    response_model=ExtractResponse,
    dependencies=[Depends(require_internal_token)],
)
async def extract(
    body: ExtractRequest,
    client: ExtractionClient = Depends(get_extraction_client),
) -> ExtractResponse:
    return await extract_posting(body, client)
