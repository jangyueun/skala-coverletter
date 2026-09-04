"""ClaudeAiProvider — SDK 없이 messages.create 만 흉내 내서 요청 조립과 응답 정리를 본다.
실제 Claude 를 부르지 않는다. 프롬프트 품질은 여기서 못 본다 — 그건 사람이 결과를 읽어야 한다."""

import asyncio
import json
from dataclasses import replace
from types import SimpleNamespace

import pytest

from app.core.config import Settings
from app.schemas.provider import DraftRequest, IntakeRequest, MatchRequest, PostingAnalysisRequest
from app.services.claude_provider import ClaudeAiProvider
from app.services.mock_provider import MockAiProvider
from app.services.provider import ProviderError

DICTIONARY = [
    {"id": 3, "name": "API 설계·연동", "category": "ROLE", "aliases": ["REST API 개발"]},
    {"id": 20, "name": "Spring Boot", "category": "TECH", "aliases": []},
]


def message(payload: dict, stop_reason: str = "end_turn", model: str = "claude-opus-5"):
    return SimpleNamespace(
        stop_reason=stop_reason, model=model,
        content=[SimpleNamespace(type="text", text=json.dumps(payload, ensure_ascii=False))],
    )


class FakeMessages:
    """create() 가 미리 넣어 둔 응답을 순서대로 돌려준다. 받은 kwargs 는 남겨 둔다."""

    def __init__(self, *responses):
        self.responses = list(responses)
        self.calls = []

    async def create(self, **kwargs):
        self.calls.append(kwargs)
        item = self.responses.pop(0)
        if isinstance(item, Exception):
            raise item
        return item


def provider(*responses):
    fake = FakeMessages(*responses)
    client = SimpleNamespace(messages=fake, close=_noop)
    return ClaudeAiProvider(replace(Settings(), anthropic_api_key="k", model="claude-opus-5"), client=client), fake


async def _noop():
    return None


def run(coro):
    return asyncio.run(coro)


def test_versions_come_from_prompt_constants():
    p, _ = provider()
    v = p.versions()
    assert (v.posting_analysis, v.experience_intake, v.match, v.draft) == ("v2", "v1", "v1", "v2")


def test_posting_analysis_drops_unknown_duplicate_and_blank_evidence():
    p, fake = provider(message({"required": [
        {"competency_id": 3, "weight": 0.9, "evidence": "REST API 설계 및 운영"},
        {"competency_id": 3, "weight": 0.5, "evidence": "중복"},
        {"competency_id": 999, "weight": 0.9, "evidence": "사전 밖"},
        {"competency_id": 20, "weight": 1.7, "evidence": "   "},
    ]}))
    request = PostingAnalysisRequest.model_validate({
        "postingId": 9, "content": "REST API 설계 및 운영", "competencies": DICTIONARY,
    })
    response = run(p.posting_analysis(request))

    assert [(r.competency_id, r.weight, r.evidence) for r in response.required] == [(3, 0.9, "REST API 설계 및 운영")]
    assert response.prompt_version == "posting_analysis/v2"
    assert response.model == "claude-opus-5"
    call = fake.calls[0]
    assert call["output_config"]["format"]["type"] == "json_schema"
    assert call["thinking"] == {"type": "adaptive"}
    assert "3. API 설계·연동 [ROLE]" in call["messages"][0]["content"]
    assert "tools" not in call


def test_intake_resumes_pause_turn_and_normalizes_candidates():
    paused = SimpleNamespace(stop_reason="pause_turn", model="claude-opus-5", content=[
        SimpleNamespace(type="server_tool_use", id="t1"),
    ])
    final = message({
        "candidates": [
            {"key": "Repo One!", "title": "MSA 주문·결제 서비스 구축", "start_date": "2026-08-15", "end_date": "2026-07-01",
             "category": "TEAM_PROJECT", "situation": "상황 ", "action": "",
             "questions": [{"field": "result", "q": "수치는?", "why": "코드에 없다"}],
             "suggested_competency_ids": [3, 999, 3]},
            {"key": "repo-one", "title": "새 프로젝트", "start_date": "언제더라", "end_date": None,
             "category": "PERSONAL_PROJECT", "situation": "", "action": "구현",
             "questions": [], "suggested_competency_ids": [20]},
        ],
        "unreadable": [{"source": "https://x", "reason": "404"}],
    })
    p, fake = provider(paused, final)
    request = IntakeRequest.model_validate({
        "links": ["https://github.com/me/repo"], "fileUrls": [],
        "existingExperiences": [{"id": 7, "title": "MSA 주문 결제 서비스 구축", "category": "TEAM_PROJECT",
                                 "startDate": None, "endDate": None}],
        "competencies": DICTIONARY,
    })
    response = run(p.experience_intake(request))

    assert len(fake.calls) == 2
    assert fake.calls[0]["tools"][0]["type"] == "web_fetch_20260209"
    # pause_turn 뒤에는 첫 사용자 메시지 + 멈춘 응답을 그대로 붙여 이어 간다
    assert [m["role"] for m in fake.calls[1]["messages"]] == ["user", "assistant"]
    assert fake.calls[1]["messages"][1]["content"] is paused.content

    first, second = response.candidates
    assert first.key == "repo-one" and second.key == "repo-one-2"
    assert first.duplicate_of_experience_id == 7          # 제목이 같은(기호·공백 무시) 등록 경험
    assert first.suggested_competency_ids == [3]          # 사전 밖 999 제외, 중복 제거
    assert str(first.start_date) == "2026-08-01"          # 월의 1일로
    assert first.end_date is None                         # 시작보다 앞선 종료는 버린다
    assert first.situation == "상황"
    assert second.start_date is None                      # 못 읽는 날짜는 null
    assert second.duplicate_of_experience_id is None
    assert response.prompt_version == "experience_intake/v1"


def draft_request(limit):
    return DraftRequest.model_validate({
        "question": {"promptText": "지원 동기", "lengthLimit": limit},
        "posting": {"company": "세움테크", "position": "백엔드", "content": "■ 담당 업무\n· 주문·정산 도메인 백엔드 개발",
                    "required": [{"name": "Spring Boot", "weight": 0.7, "evidenceLine": ""},
                                 {"name": "API 설계·연동", "weight": 0.9, "evidenceLine": "REST API 설계 및 운영"}]},
        "experiences": [{"title": "API 개발", "situation": "", "task": "목표", "action": "구현", "result": "배포"}],
    })


def test_draft_over_limit_is_cut_at_a_sentence_boundary():
    text = "첫 문장입니다. 둘째 문장입니다! 셋째 문장은 제한을 넘깁니다."     # 36자, 제한 25 → 둘째 문장 끝(18자)에서 자른다
    p, fake = provider(message({"draft": "  " + text + "  "}))
    response = run(p.draft(draft_request(25)))

    assert response.draft == "첫 문장입니다. 둘째 문장입니다!"
    assert response.char_count == len(response.draft) <= 25
    assert response.prompt_version == "draft/v2"
    prompt = fake.calls[0]["messages"][0]["content"]
    assert "글자 수 제한: 25자" in prompt
    assert "주문·정산 도메인 백엔드 개발" in prompt                              # 공고 원문
    assert prompt.index("API 설계·연동 (가중치 0.9)") < prompt.index("Spring Boot (가중치 0.7)")   # 가중치 큰 순
    assert '공고 근거: "REST API 설계 및 운영"' in prompt


def test_draft_without_posting_content_still_works():
    p, fake = provider(message({"draft": "초안"}))
    request = DraftRequest.model_validate({
        "question": {"promptText": "q", "lengthLimit": None},
        "posting": {"company": "c", "position": "p"},          # content · required 생략 — 옛 호출도 422 가 아니다
        "experiences": [],
    })
    response = run(p.draft(request))

    assert response.draft == "초안"
    assert "(원문 없음" in fake.calls[0]["messages"][0]["content"]


def test_draft_falls_back_to_hard_cut_when_no_sentence_end_fits():
    p, _ = provider(message({"draft": "가" * 120 + ". 끝"}))       # 제한 안에 문장 끝이 없다
    response = run(p.draft(draft_request(100)))

    assert response.char_count == 100 == len(response.draft)


def test_draft_within_limit_is_untouched():
    p, _ = provider(message({"draft": "짧은 초안입니다."}))
    response = run(p.draft(draft_request(700)))

    assert response.draft == "짧은 초안입니다."
    assert response.char_count == 9


def test_match_is_deterministic_and_equal_to_mock():
    request = MatchRequest.model_validate({
        "posting": {"id": 9, "required": [{"competencyId": 3, "weight": 0.9, "evidenceLine": "A"}]},
        "experiences": [{"id": 1, "title": "t", "result": "r", "competencies": [{"competencyId": 3, "strength": 0.8}]}],
    })
    p, fake = provider()
    claude = run(p.match(request))
    mock = run(MockAiProvider().match(request))

    assert fake.calls == []                                   # LLM 을 부르지 않는다
    assert (claude.overall, claude.verdict, claude.rows) == (mock.overall, mock.verdict, mock.rows)
    assert claude.prompt_version == "match/v1"


@pytest.mark.parametrize("bad", [
    message({"draft": 1}),                                    # 스키마 위반
    SimpleNamespace(stop_reason="refusal", model="m", content=[]),
    SimpleNamespace(stop_reason="end_turn", model="m", content=[SimpleNamespace(type="text", text="not json")]),
])
def test_unusable_responses_become_provider_error(bad):
    p, _ = provider(bad)
    request = DraftRequest.model_validate({
        "question": {"promptText": "q", "lengthLimit": None},
        "posting": {"company": "c", "position": "p"},
        "experiences": [],
    })
    with pytest.raises(ProviderError):
        run(p.draft(request))


def test_pause_turn_forever_becomes_provider_error():
    paused = SimpleNamespace(stop_reason="pause_turn", model="m", content=[])
    p, fake = provider(*([paused] * 8))
    request = IntakeRequest.model_validate({
        "links": ["https://github.com/me/repo"], "fileUrls": [], "existingExperiences": [], "competencies": DICTIONARY,
    })
    with pytest.raises(ProviderError):
        run(p.experience_intake(request))
    assert len(fake.calls) == 8
