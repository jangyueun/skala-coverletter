# CareerFit AI 제공자 서버

`docs/api-spec-v6.md` **8. AI 제공자 계약**을 구현한 stateless FastAPI 서버입니다.
현재 `/ai/*`는 `MockAiProvider`를 사용하며 API 키, 외부 네트워크, DB 없이 동작합니다.
프론트는 이 서버를 직접 호출하지 않습니다. Spring 워커가 호출합니다.

## Mock 서버 실행 (현재 과제)

```bash
cd ai
python3 -m venv .venv
source .venv/bin/activate
pip install -e '.[test]'
uvicorn app.main:app --reload --port 8000
```

`.env`와 Claude 키 없이 실행 가능합니다. Swagger는 `/docs`, JSON Schema를 포함한
OpenAPI는 `/openapi.json`, 서버 상태는 `/health`에서 확인합니다.
테스트는 `ai/`에서 `python -m pytest -q`로 실행합니다.

## 8절 계약

| 경로 | 동작 | promptVersion |
| --- | --- | --- |
| `GET /ai/prompts/versions` | 버전 목록 | posting_analysis: v2, 나머지 v1 |
| `POST /ai/posting-analysis` | 사전 이름/별칭이 포함된 원문 줄 추출 | posting_analysis/v2 |
| `POST /ai/experience-intake` | Mock 경험 후보와 질문 반환 | experience_intake/v1 |
| `POST /ai/match` | 입력 역량 강도와 가중치로 점수 계산 | match/v1 |
| `POST /ai/draft` | 입력 경험을 연결한 Mock 초안 반환 | draft/v1 |

POST 성공은 **200**, 잘못된 입력은 **422**, 제공자 장애/타임아웃은 **503**입니다.
검증·제공자 오류는 `{"code":"...","message":"..."}` 형태입니다.
모든 POST 응답에 최상위 `model: "mock-ai"`, `promptVersion`이 들어갑니다.
버전 조회만 명세대로 snake_case 키를 사용하고 나머지는 camelCase입니다.
`role`, `newCompetencies`, `_meta`는 새 계약 응답에 없습니다.

```bash
curl http://localhost:8000/ai/prompts/versions
curl -X POST http://localhost:8000/ai/posting-analysis \
  -H 'Content-Type: application/json' \
  -d '{"postingId":9,"content":"REST API 개발 경험이 필요합니다.","competencies":[{"id":3,"name":"API 설계·연동","category":"ROLE","aliases":["REST API 개발"]}]}'
```

```json
{
  "required": [{"competencyId":3,"weight":0.9,"evidence":"REST API 개발 경험이 필요합니다."}],
  "promptVersion":"posting_analysis/v2",
  "model":"mock-ai"
}
```

## Mock의 한계와 입력 규칙

- 공고: 키워드 일치만 사용하며 weight는 0.9로 고정입니다. 의미 분석이 아닙니다.
- 인테이크: URL을 다운로드하지 않습니다. 실제 자료 분석이나 중복 경험 판정을 하지 않으며,
  날짜/중복 ID는 null, 제안 역량은 빈 배열입니다. 확인되지 않은 사용자 성과를 생성하지 않습니다.
- 매칭: `score = min(1, Σ strength)`, `overall = Σ(weight × score) / Σ weight`.
  가중치 합이 0이면 overall 0. 갭은 근거 없음 또는 score < 0.45.
  판정은 ≥0.85 RECOMMEND, ≥0.62 CONDITIONAL, 나머지 HOLD.
- 초안: 경험 텍스트를 이어 붙이는 데모입니다. lengthLimit을 넘으면 자르고,
  charCount는 공백/줄바꿈을 포함한 Unicode 코드 포인트 수입니다.
  `lengthLimit: null`은 DB 계약처럼 제한 없음입니다.
- 사전·경험·요구 역량의 중복 ID, 범위 밖 weight/strength, 잘못된 날짜/URL은 422입니다.
  경험/요구 역량 빈 목록은 미등록 상태로 허용합니다. 인테이크는 링크/파일 중 하나가 필요합니다.

## Spring과의 책임 분리

Python은 taskId 생성, DB 저장, 큐, 폴링, 재시도를 하지 않습니다.
Spring이 작업을 예약하고 워커에서 이 서버를 호출한 뒤 결과와 model/promptVersion을 저장합니다.
실패 시 최대 3회 백오프 및 FAILED 전환도 Spring 책임입니다.
Python은 AI_REQUEST_TIMEOUT_SECONDS(기본 30초)만큼 단일 호출을 기다립니다.

현재 Spring의 DRAFT 작업 스냅샷은 questionId/experienceIds만 담고, 인테이크는
links/fileUrls만 담습니다. **워커가 문항/공고/경험 전문과 역량 사전을 조회해 8절의
전체 요청으로 조립해야 합니다.** 이 변경은 Python 계약만 구현하며 워커와 FE는 수정하지 않습니다.

## 실제 AI로 교체할 때

`app/services/provider.py`의 AiProvider를 구현하고 main.py의 제공자 생성 지점을 교체합니다.
버전 목록과 응답 promptVersion은 같은 제공자에서 결정해야 합니다. 현재 Mock과 동일한
스키마를 지키면 Spring/프론트의 응답 계약은 바뀌지 않습니다. 실제 구현 시 URL 다운로드는
별도로 크기/리다이렉트/내부 IP 접근을 제한하고, LLM 출력을 스키마 및 근거로 검증해야 합니다.
단순히 ANTHROPIC_API_KEY를 설정해도 **새 `/ai/*`는 계속 Mock**입니다.

## 인증 및 Docker

AI_INTERNAL_TOKEN 설정 시 **모든 `/ai/*` 호출**에 `Authorization: Bearer <token>`이 필요합니다.
미설정 시 로컬 인증 검사를 생략합니다. `/health`는 인증 없이 제공합니다.
Spring 공개 내부 API의 X-Internal-Token과는 별개이며 Spring → Python 호출은 Bearer입니다.
배포 시 내부망 및 긴 토큰을 설정하세요. API 키/토큰/서명된 URL을 로그나 Git에 남기지 마세요.

```bash
docker build -t careerfit-ai ./ai
docker run --rm -p 127.0.0.1:8000:8000 --env-file ai/.env careerfit-ai
```

`.env`가 없는 Mock 데모에서는 `--env-file ai/.env`를 생략할 수 있습니다.

## 이전 Claude 경로 (deprecated, 새 Spring 연동에 사용하지 않음)

`POST /internal/ai/extract`는 호환용으로 남겨 두었습니다. 기존 postingText 요청,
role/newCompetencies/_meta 응답을 사용하고 실제 Claude를 호출합니다.
아래 설명은 **이전 경로만** 실행하려는 경우에 해당합니다.

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
