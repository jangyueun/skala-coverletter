from fastapi.testclient import TestClient

from app.main import app
from app.schemas.extract import ModelExtractResult


class FakeExtractionClient:
    model = "test-model"

    async def extract(self, request):
        return (
            ModelExtractResult.model_validate(
                {
                    "required": [
                        {
                            "competencyId": 20,
                            "weight": 0.9,
                            "evidence": "Spring Boot 개발 경험이 필요합니다.",
                        },
                        {
                            "competencyId": 999,
                            "weight": 0.8,
                            "evidence": "Spring Boot 개발 경험이 필요합니다.",
                        },
                        {
                            "competencyId": 3,
                            "weight": 0.7,
                            "evidence": "원문에 없는 문장",
                        },
                    ],
                    "newCompetencies": [],
                    "role": "BACKEND",
                }
            ),
            12,
            100,
            30,
        )

    async def close(self):
        pass


def override_client():
    return FakeExtractionClient()


def test_health():
    with TestClient(app) as client:
        response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_extract_filters_unknown_id_and_fabricated_evidence():
    from app.api.extract import get_extraction_client

    app.dependency_overrides[get_extraction_client] = override_client
    try:
        with TestClient(app) as client:
            response = client.post(
                "/internal/ai/extract",
                json={
                    "postingText": "Spring Boot 개발 경험이 필요합니다.",
                    "competencies": [
                        {
                            "id": 20,
                            "name": "Spring·Spring Boot(JPA)",
                            "category": "TECH",
                            "aliases": ["Spring Boot"],
                        },
                        {
                            "id": 3,
                            "name": "API 설계·연동",
                            "category": "ROLE",
                            "aliases": ["REST API"],
                        },
                    ],
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert [item["competencyId"] for item in body["required"]] == [20]
    assert body["_meta"]["droppedInvalidResults"] == 2


def test_extract_rejects_blank_posting():
    with TestClient(app) as client:
        response = client.post(
            "/internal/ai/extract",
            json={
                "postingText": "   ",
                "competencies": [
                    {"id": 1, "name": "테스트", "category": "TECH"}
                ],
            },
        )
    assert response.status_code == 422
