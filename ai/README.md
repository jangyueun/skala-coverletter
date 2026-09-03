# CareerFit AI Server

채용공고 원문에서 요구 역량을 추출하는 내부 FastAPI 서버입니다. DB와 사용자 세션은 다루지 않고,
Spring API 서버가 전달한 원문과 역량 사전을 Claude에 보내 검증된 JSON을 반환합니다.

## 실행

Python 3.11 이상이 필요합니다.

```bash
cd ai
python -m venv .venv
source .venv/bin/activate
pip install -e '.[test]'
cp .env.example .env
# .env의 ANTHROPIC_API_KEY 입력
uvicorn app.main:app --reload --port 8000
```

- 상태 확인: `GET http://localhost:8000/health`
- API 문서: `http://localhost:8000/docs`
- 역량 추출: `POST http://localhost:8000/internal/ai/extract`

## 요청 예시

```bash
curl -X POST http://localhost:8000/internal/ai/extract \
  -H 'Content-Type: application/json' \
  -d '{
    "postingText": "Spring Boot 개발 경험이 필요합니다.",
    "competencies": [
      {
        "id": 20,
        "name": "Spring·Spring Boot(JPA)",
        "category": "TECH",
        "aliases": ["Spring Boot", "JPA"]
      }
    ]
  }'
```

`AI_INTERNAL_TOKEN`을 설정했다면 요청에 `Authorization: Bearer <token>` 헤더도 보내야 합니다.

## 테스트

```bash
cd ai
pytest
```

테스트는 가짜 AI 클라이언트를 사용하므로 API 키와 비용이 필요하지 않습니다.
