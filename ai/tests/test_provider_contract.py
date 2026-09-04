import asyncio
from copy import deepcopy
from dataclasses import replace

import pytest
from fastapi.testclient import TestClient

from app.api import extract, provider
from app.main import app
from app.schemas.provider import PromptVersions
from app.services.mock_provider import MockAiProvider
from app.services.provider import ProviderError


DICTIONARY = [{"id": 3, "name": "API 설계·연동", "category": "ROLE", "aliases": ["REST API 개발"]}]
PAYLOADS = {
    "posting-analysis": {"postingId": 9, "content": "REST API 개발 경험이 필요합니다.", "competencies": DICTIONARY},
    "experience-intake": {
        "links": ["https://github.com/example/repo"], "fileUrls": [],
        "existingExperiences": [{"id": 1, "title": "기존 경험", "category": "TEAM_PROJECT", "startDate": "2026-08-01", "endDate": None}],
        "competencies": DICTIONARY,
    },
    "match": {
        "posting": {"id": 9, "required": [{"competencyId": 3, "weight": 0.9, "evidenceLine": "REST API 개발"}]},
        "experiences": [{"id": 1, "title": "API 개발", "result": "배포 완료", "competencies": [{"competencyId": 3, "strength": 0.8}]}],
    },
    "draft": {
        "question": {"promptText": "지원 동기를 작성하세요.", "lengthLimit": 700},
        "posting": {"company": "세움테크", "position": "백엔드", "content": "[세움테크] REST API 설계 및 운영",
                    "required": [{"name": "API 설계·연동", "weight": 0.9, "evidenceLine": "REST API 설계 및 운영"}]},
        "experiences": [{"title": "API 개발", "situation": "상황", "task": "목표", "action": "구현", "result": "배포 완료"}],
    },
}


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(extract, "settings", replace(extract.settings, internal_token=""))
    # Mock 경로는 SDK를 생성하거나 외부 API를 호출하지 않아야 한다.
    from app.services import anthropic_client

    def no_sdk(*args, **kwargs):
        pytest.fail("Mock 계약이 실제 LLM 클라이언트를 생성했습니다.")

    monkeypatch.setattr(anthropic_client, "AsyncAnthropic", no_sdk)
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


def test_versions(client):
    response = client.get("/ai/prompts/versions")
    assert response.status_code == 200
    assert response.json() == {"posting_analysis": "v2", "experience_intake": "v1", "match": "v1", "draft": "v1"}


@pytest.mark.parametrize("path", PAYLOADS)
def test_contract_success_is_stateless_and_versioned(client, path):
    first = client.post(f"/ai/{path}", json=PAYLOADS[path])
    assert first.status_code == 200, first.text
    body = first.json()
    key = path.replace("-", "_")
    assert body["promptVersion"] == f"{key}/{getattr(PromptVersions(), key)}"
    assert body["model"] == "mock-ai"
    assert not {"role", "newCompetencies", "_meta", "taskId"} & body.keys()
    assert client.post(f"/ai/{path}", json=PAYLOADS[path]).json() == body


def test_posting_grounded_evidence_and_dictionary(client):
    response = client.post("/ai/posting-analysis", json=PAYLOADS["posting-analysis"]).json()
    assert response["required"] == [{"competencyId": 3, "weight": 0.9, "evidence": "REST API 개발 경험이 필요합니다."}]
    payload = deepcopy(PAYLOADS["posting-analysis"])
    payload["content"] = "사전과 관계없는 문장"
    assert client.post("/ai/posting-analysis", json=payload).json()["required"] == []


def test_intake_does_not_claim_to_read_sources(client):
    candidate = client.post("/ai/experience-intake", json=PAYLOADS["experience-intake"]).json()["candidates"][0]
    assert "Mock" in candidate["title"]
    assert candidate["startDate"] is None
    assert candidate["duplicateOfExperienceId"] is None
    assert candidate["suggestedCompetencyIds"] == []
    assert {q["field"] for q in candidate["questions"]} == {"situation", "task", "action", "result"}


def test_file_only_intake(client):
    payload = deepcopy(PAYLOADS["experience-intake"])
    payload.update(links=[], fileUrls=["https://storage.example/intake/7/790/portfolio.pdf?token=private"])
    response = client.post("/ai/experience-intake", json=payload)
    assert response.status_code == 200
    assert "private" not in response.text


@pytest.mark.parametrize("strength,verdict,gap", [(0, "HOLD", True), (0.44, "HOLD", True), (0.45, "HOLD", False), (0.62, "CONDITIONAL", False), (0.85, "RECOMMEND", False), (1, "RECOMMEND", False)])
def test_match_boundaries(client, strength, verdict, gap):
    payload = deepcopy(PAYLOADS["match"])
    payload["posting"]["required"][0]["weight"] = 1
    payload["experiences"][0]["competencies"][0]["strength"] = strength
    body = client.post("/ai/match", json=payload).json()
    assert body["overall"] == strength
    assert body["verdict"] == verdict
    assert body["rows"][0]["isGap"] is gap


def test_match_weighted_average_and_cap(client):
    payload = deepcopy(PAYLOADS["match"])
    payload["posting"]["required"] = [
        {"competencyId": 3, "weight": 0.75, "evidenceLine": "A"},
        {"competencyId": 4, "weight": 0.25, "evidenceLine": "B"},
    ]
    second = deepcopy(payload["experiences"][0])
    second["id"] = 2
    payload["experiences"].append(second)
    body = client.post("/ai/match", json=payload).json()
    assert body["overall"] == 0.75
    assert body["rows"][0]["score"] == 1
    assert body["rows"][0]["experienceIds"] == [1, 2]
    assert body["rows"][1]["experienceIds"] == []
    assert body["rows"][1]["isGap"] is True


@pytest.mark.parametrize("change", ["empty_required", "zero_weight", "no_experiences"])
def test_match_empty_states(client, change):
    payload = deepcopy(PAYLOADS["match"])
    if change == "empty_required":
        payload["posting"]["required"] = []
    elif change == "zero_weight":
        payload["posting"]["required"][0]["weight"] = 0
    else:
        payload["experiences"] = []
    response = client.post("/ai/match", json=payload)
    assert response.status_code == 200
    assert response.json()["overall"] == 0


@pytest.mark.parametrize("limit", [1, 20, 700, None])
def test_draft_length_and_count(client, limit):
    payload = deepcopy(PAYLOADS["draft"])
    payload["question"]["lengthLimit"] = limit
    payload["experiences"][0]["result"] = "한글 🙂 줄바꿈\n" * 100
    body = client.post("/ai/draft", json=payload).json()
    assert body["charCount"] == len(body["draft"])
    if limit is not None:
        assert len(body["draft"]) <= limit


@pytest.mark.parametrize("path", PAYLOADS)
def test_invalid_requests_do_not_echo_input(client, path):
    response = client.post(f"/ai/{path}", json={"secret": "must-not-leak"})
    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_FAILED"
    assert "must-not-leak" not in response.text


@pytest.mark.parametrize("case", ["blank", "duplicate_dictionary", "duplicate_experience", "duplicate_requirement", "strength", "date", "url", "empty_sources", "limit"])
def test_semantic_validation(client, case):
    path = "posting-analysis"
    payload = deepcopy(PAYLOADS[path])
    if case == "blank":
        payload["content"] = " \n "
    elif case == "duplicate_dictionary":
        payload["competencies"] *= 2
    elif case in {"duplicate_experience", "duplicate_requirement", "strength"}:
        path, payload = "match", deepcopy(PAYLOADS["match"])
        if case == "duplicate_experience":
            payload["experiences"] *= 2
        elif case == "duplicate_requirement":
            payload["posting"]["required"] *= 2
        else:
            payload["experiences"][0]["competencies"][0]["strength"] = 1.1
    elif case == "limit":
        path, payload = "draft", deepcopy(PAYLOADS["draft"])
        payload["question"]["lengthLimit"] = 0
    else:
        path, payload = "experience-intake", deepcopy(PAYLOADS["experience-intake"])
        if case == "date":
            payload["existingExperiences"][0]["endDate"] = "2025-01-01"
        elif case == "url":
            payload["links"] = ["file:///etc/passwd"]
        else:
            payload["links"] = []
    assert client.post(f"/ai/{path}", json=payload).status_code == 422


@pytest.mark.parametrize("path", ["prompts/versions", *PAYLOADS])
def test_authentication(client, monkeypatch, path):
    monkeypatch.setattr(extract, "settings", replace(extract.settings, internal_token="test-token"))
    method = "GET" if path == "prompts/versions" else "POST"
    kwargs = {} if method == "GET" else {"json": PAYLOADS[path]}
    assert client.request(method, f"/ai/{path}", **kwargs).status_code == 401
    assert client.request(method, f"/ai/{path}", headers={"Authorization": "Bearer wrong"}, **kwargs).status_code == 401
    assert client.request(method, f"/ai/{path}", headers={"Authorization": "Bearer test-token"}, **kwargs).status_code == 200


@pytest.mark.parametrize("path", PAYLOADS)
def test_provider_failure_is_503_without_retry(client, path):
    class FailingProvider(MockAiProvider):
        calls = 0

        async def fail(self, request):
            self.calls += 1
            raise ProviderError("secret external response")

        posting_analysis = experience_intake = match = draft = fail

    failing = FailingProvider()
    app.dependency_overrides[provider.get_provider] = lambda: failing
    response = client.post(f"/ai/{path}", json=PAYLOADS[path])
    assert response.status_code == 503
    assert response.json()["code"] == "AI_PROVIDER_ERROR"
    assert "secret" not in response.text
    assert failing.calls == 1


def test_timeout(client, monkeypatch):
    class SlowProvider(MockAiProvider):
        async def draft(self, request):
            await asyncio.sleep(10)

    app.dependency_overrides[provider.get_provider] = SlowProvider
    monkeypatch.setattr(provider, "settings", replace(provider.settings, request_timeout_seconds=0.001))
    assert client.post("/ai/draft", json=PAYLOADS["draft"]).status_code == 503


def test_openapi_contract(client):
    paths = client.get("/openapi.json").json()["paths"]
    for path in PAYLOADS:
        assert {"200", "422", "503"} <= paths[f"/ai/{path}"]["post"]["responses"].keys()
    assert paths["/internal/ai/extract"]["post"]["deprecated"] is True
