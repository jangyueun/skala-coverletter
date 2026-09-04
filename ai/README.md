# Career Lab AI 제공자 서버

`docs/api-spec-v6.md` **8. AI 제공자 계약**을 구현한 stateless FastAPI 서버입니다.
`ANTHROPIC_API_KEY`가 있으면 `ClaudeAiProvider`(실제 Claude), 없으면 `MockAiProvider`(키·외부 네트워크·DB 없이 동작)로 뜹니다.
어느 쪽인지는 시작 로그 `AI provider: ...`에 찍힙니다. 프론트는 이 서버를 직접 호출하지 않습니다. Spring 워커가 호출합니다.

## Claude 로 돌리기

```bash
cp .env.example .env      # ANTHROPIC_API_KEY 를 채운다
uvicorn app.main:app --reload --port 8000
# 또는 루트에서 docker compose up --build ai   (compose 가 ai/.env 를 읽는다)
```

| 계약 | 방식 | 프롬프트 |
| --- | --- | --- |
| `POST /ai/posting-analysis` | Claude, 구조화 출력 | `app/services/prompts.py` `POSTING_ANALYSIS` |
| `POST /ai/experience-intake` | Claude + `web_fetch`(링크·파일 URL 을 모델이 직접 읽음) + **advisor**(Opus 5, 판단이 갈릴 때만), pause_turn 이어가기 | `EXPERIENCE_INTAKE` (v2) |
| `POST /ai/match` | **LLM 없음** — 결정론 공식(`app/services/matching.py`), 프론트 카드와 같은 식 | 버전만 관리 |
| `POST /ai/draft` | Claude, 구조화 출력. 공고 원문(`posting.content`)과 요구 역량별 근거 문장(`posting.required`)에 경험을 맞댄다. lengthLimit 을 넘기면 문장 경계에서 자름 | `DRAFT` (v2) |

- **프롬프트 버저닝** — 프롬프트 문장을 고치면 `prompts.py`의 그 항목 `version`을 올린다. 응답 `promptVersion`과
  `GET /ai/prompts/versions`가 거기서 나가고, Spring은 그 값을 `ai_tasks.prompt_version`에 저장하고 멱등 키에 넣는다.
  버전을 안 올리면 같은 입력의 작업이 옛 프롬프트 결과를 재사용한다.
- 모델 출력은 스키마(json_schema)로 받지만 **값**은 다시 거른다 — 사전 밖 `competency_id`, 중복, 근거 없는 항목, 시작보다
  앞선 종료일, 제한을 넘는 초안 길이. 프롬프트로 지시했다고 검증을 생략하면 지어낸 id가 매칭 점수로 흘러든다.
- **기능별 모델** — 공고 분석·인테이크는 `claude-sonnet-5`, 초안은 `AI_MODEL`(기본 `claude-opus-5`)이다.
  `AI_MODEL_POSTING_ANALYSIS` · `AI_MODEL_EXPERIENCE_INTAKE` · `AI_MODEL_DRAFT` 로 따로 바꾸고, `AI_EFFORT_*` 로 생각 깊이를
  조절한다(`app/core/config.py`). 응답 `model` 은 실제로 답한 모델이라 작업마다 다를 수 있다.
- **인테이크 advisor** — 실행 모델(Sonnet 5)이 자료를 읽고 문장을 쓰며, 여러 자료가 같은 프로젝트인지·후보를 나눌지 합칠지·
  어떤 역량이 증명되는지 애매할 때만 advisor(Opus 5, `AI_ADVISOR_MODEL`)에게 묻는다(`AI_ADVISOR_MAX_USES`, 기본 2회).
  advisor 는 실행 모델의 맥락을 그대로 받으므로 읽은 자료가 많을수록 한 번 부르는 값이 Opus 가격으로 커진다 — 그래서
  횟수를 막고 프롬프트로 "판단이 갈리는 순간에만" 부르게 한다. Opus 5 의 조언은 암호화 블록으로 와서 우리가 읽을 수 없고
  그대로 되돌려 보내기만 한다. 베타 헤더(`advisor-tool-2026-03-01`)가 필요해 인테이크만 `client.beta.messages` 로 간다.
  로그에 `advisor 호출 N회` 가 남는다. `AI_ADVISOR_EXPERIENCE_INTAKE=0` 으로 끈다.
- **프롬프트 캐시** — 시스템 프롬프트와 역량 사전(51개 · 약 5,700 토큰)은 호출마다 같아서 `system` 블록에 두고 마지막
  블록에 `cache_control` 을 단다. 공고 원문·경험처럼 매번 다른 것만 `messages` 로 간다. 사전은 id 순으로 고정한다 —
  캐시는 바이트 단위 접두사 일치라 순서가 흔들리면 매번 새로 쓴다. TTL 은 `AI_CACHE_TTL`(기본 `1h`).
  호출마다 `Claude <model> 토큰 input=… cache_write=… cache_read=…` 로그가 남는다. 둘째 호출부터 `cache_read` 가
  사전 크기만큼 찍혀야 정상이고, 계속 0 이면 접두사가 바뀌고 있는 것이다.
- 한 호출 상한은 `AI_REQUEST_TIMEOUT_SECONDS`(기본 300초). 인테이크는 저장소를 여러 번 읽어 몇 분이 걸린다.
  Spring 쪽 `careerlab.ai.read-timeout`(기본 330초)이 이보다 길어야 한다.
- 실패(인증·한도·연결·거부·해석 실패)는 전부 503 `AI_PROVIDER_ERROR`다. 원인은 서버 로그에만 남고 키·URL·원문은 안 남긴다.
  Spring이 3회 재시도한 뒤 작업을 FAILED로 둔다.
- 테스트: `python -m pytest -q` — `tests/test_claude_provider.py`는 SDK를 흉내 내서 요청 조립·응답 정리만 본다. 프롬프트 품질은
  사람이 결과를 읽어야 한다.

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

## 다른 제공자로 바꿀 때

`app/services/provider.py`의 AiProvider를 구현하고 `main.py`의 `build_provider()`에서 고릅니다.
버전 목록과 응답 promptVersion은 같은 제공자에서 결정해야 합니다. Mock과 동일한 스키마를 지키면
Spring/프론트의 응답 계약은 바뀌지 않습니다. 인테이크의 URL 읽기는 Claude의 `web_fetch`(서버 도구)가 맡아
우리 서버가 직접 다운로드하지 않습니다 — 직접 받게 바꾸면 크기/리다이렉트/내부 IP 접근을 따로 제한해야 합니다.

## 인증 및 Docker

AI_INTERNAL_TOKEN 설정 시 **모든 `/ai/*` 호출**에 `Authorization: Bearer <token>`이 필요합니다.
미설정 시 로컬 인증 검사를 생략합니다. `/health`는 인증 없이 제공합니다.
Spring 공개 내부 API의 X-Internal-Token과는 별개이며 Spring → Python 호출은 Bearer입니다.
배포 시 내부망 및 긴 토큰을 설정하세요. API 키/토큰/서명된 URL을 로그나 Git에 남기지 마세요.

```bash
docker build -t careerlab-ai ./ai
docker run --rm -p 127.0.0.1:8000:8000 --env-file ai/.env careerlab-ai
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
