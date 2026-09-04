from typing import Protocol

from app.schemas.extract import (
    ExtractMeta,
    ExtractRequest,
    ExtractResponse,
    ModelExtractResult,
)


class ExtractionClient(Protocol):
    model: str

    async def extract(
        self, request: ExtractRequest
    ) -> tuple[ModelExtractResult, int, int | None, int | None]: ...


async def extract_posting(
    request: ExtractRequest,
    client: ExtractionClient,
) -> ExtractResponse:
    result, latency_ms, input_tokens, output_tokens = await client.extract(request)
    known_ids = {competency.id for competency in request.competencies}
    seen_ids: set[int] = set()
    valid_required = []

    for item in result.required:
        valid = (
            item.competency_id in known_ids
            and item.competency_id not in seen_ids
            and item.evidence in request.posting_text
        )
        if not valid:
            continue
        seen_ids.add(item.competency_id)
        valid_required.append(item)

    return ExtractResponse(
        required=valid_required,
        newCompetencies=result.new_competencies,
        role=result.role,
        _meta=ExtractMeta(
            model=client.model,
            latencyMs=latency_ms,
            inputTokens=input_tokens,
            outputTokens=output_tokens,
            droppedInvalidResults=len(result.required) - len(valid_required),
        ),
    )
