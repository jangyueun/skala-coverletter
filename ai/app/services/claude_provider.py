"""Claude 로 8절 계약을 실제로 수행하는 제공자. ANTHROPIC_API_KEY 가 있을 때만 main.py 가 이걸 고른다.

세 가지가 LLM 을 부른다 — 공고 분석 · 경험 인테이크(web_fetch 로 링크·파일을 읽는다) · 자소서 초안.
매칭은 결정론 공식(services/matching.py)이라 Claude 를 부르지 않는다.

출력은 전부 구조화 출력(output_config.format = json_schema)으로 받는다 — 파싱 실패를 걱정할 필요가 없고,
모델이 스키마 밖의 필드를 만들 수 없다. 그래도 사전 밖 competency_id 같은 **값**은 여기서 다시 거른다.
프롬프트로 지시했다고 검증을 생략하면 지어낸 id 가 매칭 점수로 흘러든다.

실패는 전부 ProviderError 하나로 — API 레이어가 503 으로 바꾸고 Spring 이 재시도한다. 키·URL·원문은 로그에 안 남긴다.
"""

import json
import logging
import re
from datetime import date
from typing import Literal

import anthropic
from anthropic import AsyncAnthropic
from pydantic import BaseModel, ConfigDict, ValidationError

from app.core.config import Settings
from app.schemas.provider import (
    Candidate, Category, Competency, DraftRequest, DraftResponse, IntakeQuestion,
    IntakeRequest, IntakeResponse, MatchRequest, MatchResponse, PostingAnalysisRequest,
    PostingAnalysisResponse, PromptVersions, RequiredCompetency,
)
from app.services import prompts
from app.services.matching import compute_match
from app.services.provider import ProviderError

logger = logging.getLogger(__name__)

MAX_OUTPUT_TOKENS = 16000
# advisor 도구는 아직 베타다 — 이 헤더가 있어야 하고 client.beta.messages 로 불러야 한다.
ADVISOR_BETA = "advisor-tool-2026-03-01"
# web_fetch 는 서버 도구라 한 요청 안에서 여러 턴을 돈다. 서버 쪽 반복 한도에 걸리면 pause_turn 으로 돌아오고,
# 지금까지의 응답을 그대로 붙여 다시 부르면 이어서 한다. 무한히 이어 가지 않게 상한을 둔다.
MAX_PAUSE_TURNS = 8


# ── 모델이 돌려줘야 하는 모양. 계약 스키마와 따로 두는 이유 — 모델에게는 우리가 채울 필드(duplicate_of_experience_id)나
#    응답에 못 싣는 필드(unreadable)를 다르게 줘야 한다. extra="forbid" 가 additionalProperties:false 를 만든다.

class _Out(BaseModel):
    model_config = ConfigDict(extra="forbid")


class _Required(_Out):
    competency_id: int
    weight: float
    evidence: str


class _AnalysisOut(_Out):
    required: list[_Required]


class _Question(_Out):
    field: Literal["situation", "task", "action", "result"]
    q: str
    why: str


class _CandidateOut(_Out):
    key: str
    title: str
    start_date: str | None
    end_date: str | None
    category: Category
    situation: str
    action: str
    questions: list[_Question]
    suggested_competency_ids: list[int]


class _Unreadable(_Out):
    source: str
    reason: str


class _IntakeOut(_Out):
    candidates: list[_CandidateOut]
    unreadable: list[_Unreadable]


class _DraftOut(_Out):
    draft: str


def _schema(model: type[BaseModel]) -> dict:
    return {"type": "json_schema", "schema": model.model_json_schema()}


def _dictionary(competencies: list[Competency]) -> str:
    """id 순으로 고정한다. 캐시는 바이트 단위 접두사 일치라, Spring 이 주는 순서가 흔들리면 매번 새로 쓴다."""
    return "\n".join(
        f"{c.id}. {c.name} [{c.category}]" + (f" — {', '.join(c.aliases[:8])}" if c.aliases else "")
        for c in sorted(competencies, key=lambda c: c.id)
    )


def _normalize_title(title: str) -> str:
    return re.sub(r"[\s\W_]+", "", title).casefold()


_SENTENCE_END = re.compile(r"[.!?。](?=\s|$)")


def _fit(draft: str, limit: int | None) -> str:
    """제한을 넘긴 초안을 **문장 경계**에서 자른다. 글자 수로 뚝 자르면 "…여러 시스템이 연" 처럼 말이 끊긴다.

    프롬프트로 제한을 지키라고 해도 몇십 자 넘길 때가 있다. 제한 안의 마지막 문장 끝에서 자르되, 그게 제한의 70%
    밑으로 내려가면(문장이 아주 길 때) 그냥 글자 수로 자른다 — 너무 짧은 초안보다는 끊긴 한 문장이 낫다."""
    if limit is None or len(draft) <= limit:
        return draft
    head = draft[:limit]
    ends = [m.end() for m in _SENTENCE_END.finditer(head)]
    if ends and ends[-1] >= limit * 0.7:
        return head[:ends[-1]].rstrip()
    return head.rstrip()


def _month(value: str | None) -> date | None:
    """모델이 준 'YYYY-MM-01' 만 받는다. 다른 형식은 확인 안 된 것으로 보고 null."""
    if not value:
        return None
    try:
        parsed = date.fromisoformat(value)
    except ValueError:
        return None
    return parsed.replace(day=1)


class ClaudeAiProvider:
    """client 를 주입받는 이유는 테스트다 — 실제 SDK 없이 messages.create 만 흉내 낸다."""

    def __init__(self, config: Settings, client: AsyncAnthropic | None = None):
        self.model = config.model
        self._config = config
        self._client = client or AsyncAnthropic(
            api_key=config.anthropic_api_key,
            timeout=config.request_timeout_seconds,
        )

    async def close(self) -> None:
        await self._client.close()

    def versions(self) -> PromptVersions:
        return PromptVersions(
            posting_analysis=prompts.POSTING_ANALYSIS.version,
            experience_intake=prompts.EXPERIENCE_INTAKE.version,
            match=prompts.MATCH_VERSION,
            draft=prompts.DRAFT.version,
        )

    # ── 공고 분석 ───────────────────────────────────────────────

    async def posting_analysis(self, request: PostingAnalysisRequest) -> PostingAnalysisResponse:
        system = self._system(
            prompts.POSTING_ANALYSIS.system,
            f"# 역량 사전 (이 안에서만 고른다)\n{_dictionary(request.competencies)}",
        )
        user = f"""# 채용공고 원문
{request.content}"""
        message = await self._create(
            model=self._config.model_posting_analysis, effort=self._config.effort_posting_analysis,
            system=system, content=user, schema=_AnalysisOut,
        )
        parsed = self._parse(message, _AnalysisOut)

        known = {c.id for c in request.competencies}
        seen: set[int] = set()
        required = []
        for item in parsed.required:
            if item.competency_id not in known or item.competency_id in seen or not item.evidence.strip():
                continue
            seen.add(item.competency_id)
            required.append(RequiredCompetency(
                competency_id=item.competency_id,
                weight=min(1.0, max(0.0, item.weight)),
                evidence=item.evidence.strip(),
            ))
        dropped = len(parsed.required) - len(required)
        if dropped:
            logger.info("posting_analysis: 사전 밖·중복·근거 없는 항목 %d건 제외", dropped)
        return PostingAnalysisResponse(
            required=required, prompt_version=prompts.POSTING_ANALYSIS.label,
            model=message.model or self._config.model_posting_analysis,
        )

    # ── 경험 인테이크 ───────────────────────────────────────────

    async def experience_intake(self, request: IntakeRequest) -> IntakeResponse:
        sources = [str(url) for url in [*request.links, *request.file_urls]]
        existing = "\n".join(
            f"- #{e.id} {e.title} [{e.category}] {e.start_date or '?'}~{e.end_date or ''}"
            for e in request.existing_experiences
        ) or "(없음)"
        system = self._system(
            prompts.EXPERIENCE_INTAKE.system,
            f"# 역량 사전 (suggested_competency_ids 는 이 안에서만)\n{_dictionary(request.competencies)}",
        )
        user = f"""# 이미 등록된 경험 (같은 프로젝트면 후보로 내지 않는다)
{existing}

# 읽을 자료 — 전부 web_fetch 로 직접 읽어라
{chr(10).join(f'- {s}' for s in sources)}

위 자료에서 경험 후보를 뽑아라."""
        tools = [{
            "type": "web_fetch_20260209",
            "name": "web_fetch",
            # 실패한 fetch 도 이 수에 든다. GitHub 프로필 하나를 주면 저장소 목록 → 저장소 → README 로 들어가므로
            # 링크 수보다 훨씬 넉넉해야 한다 — 8회로는 저장소 25개 중 6개만 보고 한도에 걸렸다(실측). 캐시가 반복 읽기를 0.1배로 만든다.
            "max_uses": min(24, len(sources) * 6 + 10),
            # 500kB PDF 하나가 12만 토큰이다. 상한을 안 두면 포트폴리오 한 건이 컨텍스트를 통째로 먹는다.
            "max_content_tokens": 40000,
        }]
        betas = None
        if self._config.advisor_experience_intake:
            tools.append(self._advisor_tool())
            betas = [ADVISOR_BETA]
        message = await self._create(
            model=self._config.model_experience_intake, effort=self._config.effort_experience_intake,
            system=system, content=user, schema=_IntakeOut, tools=tools, betas=betas,
        )
        parsed = self._parse(message, _IntakeOut)

        known = {c.id for c in request.competencies}
        by_title = {_normalize_title(e.title): e.id for e in request.existing_experiences}
        candidates = []
        used_keys: set[str] = set()
        dropped_ids = 0
        for index, c in enumerate(parsed.candidates):
            ids = [i for i in c.suggested_competency_ids if i in known]
            dropped_ids += len(c.suggested_competency_ids) - len(ids)
            key = re.sub(r"[^a-z0-9-]+", "-", c.key.casefold()).strip("-") or f"candidate-{index + 1}"
            while key in used_keys:
                key = f"{key}-{index + 1}"
            used_keys.add(key)
            start, end = _month(c.start_date), _month(c.end_date)
            if start and end and end < start:
                end = None
            candidates.append(Candidate(
                key=key, title=c.title.strip() or f"경험 후보 {index + 1}", category=c.category,
                start_date=start, end_date=end,
                situation=c.situation.strip(), action=c.action.strip(),
                questions=[IntakeQuestion(field=q.field, q=q.q, why=q.why) for q in c.questions],
                suggested_competency_ids=list(dict.fromkeys(ids)),
                # 제목이 같은 등록 경험이 있으면 그 id — 프론트가 "이미 등록됨" 으로 잠근다.
                duplicate_of_experience_id=by_title.get(_normalize_title(c.title)),
            ))
        if dropped_ids:
            logger.info("experience_intake: 사전 밖 competency_id %d건 제외", dropped_ids)
        if parsed.unreadable:
            # 계약 응답에는 자리가 없다(§8). 건수와 사유만 남긴다 — URL 은 남기지 않는다.
            logger.info("experience_intake: 읽지 못한 자료 %d건 — %s", len(parsed.unreadable),
                        " / ".join(u.reason[:80] for u in parsed.unreadable))
        logger.info("experience_intake: 후보 %d건 (모델이 낸 %d건)", len(candidates), len(parsed.candidates))
        return IntakeResponse(
            candidates=candidates, prompt_version=prompts.EXPERIENCE_INTAKE.label,
            model=message.model or self._config.model_experience_intake,
        )

    # ── 매칭 (결정론) ───────────────────────────────────────────

    async def match(self, request: MatchRequest) -> MatchResponse:
        overall, verdict, rows = compute_match(request)
        return MatchResponse(
            overall=overall, verdict=verdict, rows=rows,
            prompt_version=f"match/{prompts.MATCH_VERSION}", model="rule-based",
        )

    # ── 자소서 초안 ─────────────────────────────────────────────

    async def draft(self, request: DraftRequest) -> DraftResponse:
        limit = request.question.length_limit
        experiences = "\n\n".join(
            f"""## {e.title}
- S(상황): {e.situation or '(비어 있음)'}
- T(과제): {e.task or '(비어 있음)'}
- A(행동): {e.action or '(비어 있음)'}
- R(결과): {e.result or '(비어 있음)'}"""
            for e in request.experiences
        )
        required = sorted(request.posting.required, key=lambda r: -r.weight)
        requirements = "\n".join(
            f"- {r.name} (가중치 {r.weight:.1f})" + (f" — 공고 근거: \"{r.evidence_line}\"" if r.evidence_line else "")
            for r in required
        ) or "(명시 없음)"
        content = request.posting.content.strip() or "(원문 없음 — 회사·직무·요구 역량만으로 쓴다)"
        user = f"""# 지원 공고
{request.posting.company} · {request.posting.position}

## 요구 역량 (가중치 큰 순)
{requirements}

## 공고 원문
{content}

# 문항
{request.question.prompt_text}
글자 수 제한: {f'{limit}자 (공백 포함)' if limit else '없음'}

# 근거 경험 (이것만 재료로 쓴다)
{experiences}

위 문항에 대한 자기소개서 초안을 써라."""
        message = await self._create(
            model=self._config.model_draft, effort=self._config.effort_draft,
            system=self._system(prompts.DRAFT.system), content=user, schema=_DraftOut,
        )
        parsed = self._parse(message, _DraftOut)

        draft = _fit(parsed.draft.strip(), limit)
        return DraftResponse(
            draft=draft, char_count=len(draft), prompt_version=prompts.DRAFT.label,
            model=message.model or self._config.model_draft,
        )

    # ── 공통 ───────────────────────────────────────────────────

    def _system(self, *texts: str) -> list[dict]:
        """호출마다 같은 것(프롬프트 · 역량 사전)을 system 블록으로 두고 마지막 블록에 캐시 표시를 단다.

        캐시는 tools → system → messages 순서의 접두사가 바이트까지 같아야 맞는다. 그래서 사전은 사용자 메시지가 아니라
        여기 있고, 공고 원문·경험처럼 호출마다 다른 것은 messages 로 간다. 접두사가 모델별 최소 길이(Opus 5 는 512,
        Sonnet 5 는 1024 토큰)보다 짧으면 조용히 캐시되지 않는다 — 오류는 아니다. 실제로 맞는지는 로그의 cache_read 로 본다."""
        blocks = [{"type": "text", "text": text} for text in texts]
        blocks[-1]["cache_control"] = {"type": "ephemeral", "ttl": self._config.cache_ttl}
        return blocks

    def _advisor_tool(self) -> dict:
        """인테이크의 advisor. 실행 모델(Sonnet)이 판단이 필요할 때 부르는 무거운 모델(Opus).

        Opus 5 를 advisor 로 두면 조언이 암호화 블록(advisor_redacted_result)으로 온다 — 우리는 읽을 수 없고 그대로
        되돌려 보내기만 하면 되는데, pause_turn 이어가기가 응답 블록을 통째로 붙이므로 그 조건은 이미 맞는다.
        caching 은 advisor 자신의 프롬프트(실행 모델의 맥락 전체) 캐시다 — 한 요청에서 두 번 부르면 둘째가 싸진다."""
        return {
            "type": "advisor_20260301",
            "name": "advisor",
            "model": self._config.advisor_model,
            "max_uses": self._config.advisor_max_uses,
            "max_tokens": max(1024, self._config.advisor_max_tokens),
            "caching": {"type": "ephemeral", "ttl": self._config.cache_ttl},
        }

    async def _create(
        self, *, model: str, effort: str, system: list[dict], content: str, schema: type[BaseModel],
        tools: list | None = None, betas: list[str] | None = None,
    ):
        """구조화 출력으로 한 번 부른다. 서버 도구가 pause_turn 을 주면 응답을 그대로 붙여 이어 간다.
        model·effort 는 기능마다 다르다(core/config.py) — 여기서는 받은 값을 그대로 쓴다.
        betas 가 있으면 베타 엔드포인트(client.beta.messages)로 간다 — advisor 도구가 그렇다."""
        messages: list[dict] = [{"role": "user", "content": content}]
        kwargs: dict = {
            "model": model,
            "max_tokens": MAX_OUTPUT_TOKENS,
            "system": system,
            # Opus 5 는 기본이 adaptive 지만, 모델을 바꿔도 같은 동작이 나게 명시한다.
            "thinking": {"type": "adaptive"},
            "output_config": {"format": _schema(schema), "effort": effort},
        }
        if tools:
            kwargs["tools"] = tools
        if betas:
            kwargs["betas"] = betas
        endpoint = self._client.beta.messages if betas else self._client.messages
        try:
            for _ in range(MAX_PAUSE_TURNS):
                message = await endpoint.create(messages=messages, **kwargs)
                self._log_usage(model, message)
                if message.stop_reason != "pause_turn":
                    return message
                # 내용을 요약해서 넣으면 도구 결과가 깨진다 — 응답 블록을 그대로 붙인다.
                messages = [messages[0], {"role": "assistant", "content": message.content}]
            logger.warning("Claude 호출이 pause_turn 을 %d번 넘겨 중단", MAX_PAUSE_TURNS)
            raise ProviderError
        except anthropic.AuthenticationError:
            logger.error("Claude 인증 실패 — ANTHROPIC_API_KEY 를 확인하세요")
            raise ProviderError from None
        except anthropic.RateLimitError:
            logger.warning("Claude 요청 한도 초과(429)")
            raise ProviderError from None
        except anthropic.APIStatusError as error:
            logger.warning("Claude 응답 오류 status=%s type=%s", error.status_code, getattr(error, "type", None))
            raise ProviderError from None
        except anthropic.APIConnectionError:
            logger.warning("Claude 에 연결하지 못함(타임아웃 포함)")
            raise ProviderError from None

    @staticmethod
    def _log_usage(model: str, message) -> None:
        """호출 한 번의 토큰. cache_read 가 0 이면 캐시가 안 맞은 것 — 사전 순서가 흔들렸거나 접두사가 너무 짧다.
        advisor 를 몇 번 불렀는지도 여기 남긴다 — 조언 내용은 암호화라 못 보지만, 부른 횟수와 실패는 보인다."""
        usage = getattr(message, "usage", None)
        if usage is not None:
            logger.info(
                "Claude %s 토큰 input=%s cache_write=%s cache_read=%s output=%s stop=%s",
                model, getattr(usage, "input_tokens", None),
                getattr(usage, "cache_creation_input_tokens", None), getattr(usage, "cache_read_input_tokens", None),
                getattr(usage, "output_tokens", None), message.stop_reason,
            )
        results = [b for b in (getattr(message, "content", None) or []) if getattr(b, "type", None) == "advisor_tool_result"]
        if results:
            errors = [
                getattr(b.content, "error_code", None) for b in results
                if getattr(getattr(b, "content", None), "type", None) == "advisor_tool_result_error"
            ]
            logger.info("advisor 호출 %d회%s", len(results), f" (실패 {errors})" if errors else "")

    @staticmethod
    def _parse(message, schema: type[BaseModel]):
        if message.stop_reason == "refusal":
            logger.warning("Claude 가 요청을 거부함(refusal)")
            raise ProviderError
        text = next((block.text for block in message.content if block.type == "text"), None)
        if text is None:
            logger.warning("Claude 응답에 text 블록이 없음 stop_reason=%s", message.stop_reason)
            raise ProviderError
        try:
            return schema.model_validate(json.loads(text))
        except (json.JSONDecodeError, ValidationError) as error:
            logger.warning("Claude 응답 해석 실패: %s", type(error).__name__)
            raise ProviderError from None
