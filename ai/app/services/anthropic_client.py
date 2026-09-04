import json
import time

from anthropic import AsyncAnthropic
from fastapi import HTTPException, status

from app.core.config import Settings
from app.schemas.extract import ExtractRequest, ModelExtractResult


SYSTEM_PROMPT = """너는 채용공고에서 요구 역량을 뽑는다.

지켜야 할 것:
- 주어진 역량 사전 안에서만 고른다. 사전에 없는 것을 competencyId로 지어내지 마라.
  사전으로 표현할 수 없는 요구사항은 newCompetencies에 문자열로 넣어라.
- evidence는 공고 원문 문장을 그대로 옮긴다. 요약하거나 다듬지 마라.
  근거를 확인할 수 없으면 그 역량을 넣지 마라.
- weight는 공고가 그것을 어디에 뒀는지로 판단한다.
  자격요건 상단 > 자격요건 하단 > 우대사항 > 인재상 순으로 무겁다.
- 없는 것을 지어내는 것보다 적게 뽑는 것이 낫다. 이 결과가 사용자의 매칭 점수를 움직인다.
"""


class AnthropicExtractionClient:
    def __init__(self, config: Settings):
        self.model = config.model
        self._api_key = config.anthropic_api_key
        self._client = AsyncAnthropic(
            api_key=self._api_key or "not-configured",
            timeout=config.request_timeout_seconds,
        )

    async def close(self) -> None:
        await self._client.close()

    async def extract(
        self, request: ExtractRequest
    ) -> tuple[ModelExtractResult, int, int | None, int | None]:
        if not self._api_key:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="AI API 키가 설정되지 않았습니다.",
            )

        dictionary = "\n".join(
            f"{item.id}. {item.name} [{item.category}] — {', '.join(item.aliases)}"
            for item in request.competencies
        )
        prompt = f"""# 역량 사전 (이 안에서만 고른다)
{dictionary}

# 채용공고 원문
{request.posting_text}"""
        started_at = time.monotonic()

        try:
            message = await self._client.messages.create(
                model=self.model,
                max_tokens=4096,
                system=SYSTEM_PROMPT,
                messages=[{"role": "user", "content": prompt}],
                output_config={
                    "format": {
                        "type": "json_schema",
                        "schema": ModelExtractResult.model_json_schema(
                            by_alias=True, mode="serialization"
                        ),
                    }
                },
            )
            text_block = next(
                block.text for block in message.content if block.type == "text"
            )
            parsed = ModelExtractResult.model_validate(json.loads(text_block))
        except HTTPException:
            raise
        except Exception as error:
            error_status = getattr(error, "status_code", None)
            if error_status == 401:
                code = status.HTTP_401_UNAUTHORIZED
                message_text = "AI API 키가 올바르지 않습니다."
            elif error_status == 429:
                code = status.HTTP_429_TOO_MANY_REQUESTS
                message_text = "AI 요청이 몰렸습니다. 잠시 후 다시 시도해 주세요."
            else:
                code = status.HTTP_502_BAD_GATEWAY
                message_text = "AI 호출 또는 응답 해석에 실패했습니다."
            raise HTTPException(status_code=code, detail=message_text) from error

        usage = message.usage
        return (
            parsed,
            round((time.monotonic() - started_at) * 1000),
            getattr(usage, "input_tokens", None),
            getattr(usage, "output_tokens", None),
        )
