# Career Fit 현재 ERD 및 API 명세 정리

작성일: 2026-09-03 14:19 KST

## 1. 현재 상태 한눈에 보기

| 구분 | 현재 상태 |
|---|---|
| Career DB ERD | 15개 테이블로 최종 수정안 작성됨 |
| AI Service DB ERD | 2개 테이블로 물리 분리됨 |
| 실제 DB 생성 방식 | Flyway `V1__create_career_fit_schema.sql` |
| Hibernate | `ddl-auto:validate` |
| 인증 | Slack OAuth + 서버 세션 |
| 향후 인증 확장 | Spring Security JWT 가능. 현재 ERD 변경 불필요 |
| 최종 권장 API | Auth 포함 20개, 개발용 mock 제외 시 운영 19개 |
| 실제 Java 구현 API | Slack 인증 4개만 구현됨 |
| Notion API 명세 | 아직 최종 수정안이 반영되지 않은 상태 |

즉, ERD 설계와 Career DB의 최초 Flyway SQL은 준비됐지만 기능 API는 아직 구현 전이다.
API 담당자는 이 문서의 최종 URL과 요청·응답 계약을 Notion 상세 페이지에 반영해야 한다.

## 2. 전체 데이터 흐름

```text
Slack 로그인
  → users 생성 또는 로그인 정보 갱신

경험 등록
  → experiences 저장
  → experience_competencies에 경험별 역량과 strength 저장

채용공고 수집/등록
  → companies, job_postings 저장
  → posting_competencies에 공고 요구 역량과 weight 저장
  → job_posting_questions에 공고별 자기소개서 문항 저장

AI 매칭 요청
  → AI Service DB의 ai_tasks 생성
  → 프론트는 다른 화면을 사용할 수 있음
  → 완료 결과를 job_matches에 user + posting 기준으로 저장

AI 자기소개서 초안 요청
  → 사용자가 문항과 경험을 선택
  → AI Service DB의 ai_tasks 생성
  → 완료된 초안을 문항 화면에 표시
  → 사용자가 저장하면 cover_letter_answers의 현재 답변 1건을 INSERT 또는 UPDATE
```

지원서 생성 버튼과 `applications` 테이블은 없다. 사용자가 로그인하고 경험을 등록하면 공고별 매칭을
볼 수 있고, 공고에 이미 등록된 문항을 기준으로 자기소개서를 작성한다.

## 3. Career DB ERD — 15개 테이블

### 3.1 사용자

#### `users`

Slack 로그인 사용자를 저장한다.

- PK: `id`
- 사용자 식별 키: `(slack_team_id, slack_user_id)` UNIQUE
- `email`은 Slack 설정에 따라 없거나 바뀔 수 있으므로 식별 키가 아니다.
- `display_name`, `avatar_url`은 로그인할 때 Slack 최신 정보로 갱신한다.
- 현재는 세션 로그인이고, 향후 JWT를 사용해도 JWT subject에 `users.id`를 넣으면 ERD 변경이 필요 없다.

### 3.2 역량 사전

#### `competencies`

Java, 협업, 금융 도메인 지식처럼 시스템이 공통으로 사용하는 표준 역량이다.

- `name` 전역 UNIQUE
- `category`: `TECH`, `SOFT`, `DOMAIN`, `VALUE`

#### `competency_aliases`

표준 역량의 동의어를 별도 행으로 저장한다.

- 예: 표준 역량 `Java`에 `자바`, `JAVA` 등의 별칭 연결
- `competency_id → competencies.id`
- `alias` 전역 UNIQUE: 별칭 하나가 서로 다른 두 역량에 동시에 연결되는 것을 방지

#### `competency_candidates`

AI가 공고에서 찾았지만 아직 표준 역량으로 확정하지 못한 단어를 검수하기 위한 테이블이다.

- `job_posting_id → job_postings.id`
- 승인 시 `merged_competency_id → competencies.id`
- 검수자 `reviewed_by → users.id`
- `(job_posting_id, raw_term)` UNIQUE

### 3.3 사용자 경험

#### `experiences`

사용자가 등록한 경험의 STAR 내용과 기본 정보를 저장한다.

- `user_id → users.id`
- `situation`, `task`, `action`, `result`
- `source`: 직접 등록 `MANUAL` 또는 AI 입력 정리 `AI_INTAKE`
- `source_ai_task_id`: AI DB 작업 ID의 논리 참조이며 물리 FK는 없다.

#### `experience_competencies`

경험과 역량의 다대다 연결 테이블이다.

- PK: `(experience_id, competency_id)`
- `experience_id → experiences.id`
- `competency_id → competencies.id`
- `strength`: 해당 경험에서 역량이 드러나는 정도, 0 이상 1 이하

### 3.4 공고와 공고 문항

#### `companies`

회사 원본 이름과 중복 판별용 정규화 이름을 저장한다.

- `normalized_name` UNIQUE

#### `job_postings`

채용공고 본문, 직무, 마감일, 수집 상태를 저장한다.

- `company_id → companies.id`
- `deadline=NULL`: 상시채용
- `source`: `CRAWLED`, `MANUAL`
- `status`: `ACTIVE`, `CLOSED`, `DELETED`, `COLLECT_FAILED`
- `question_collection_status`: 공고 문항 수집 상태
- `source_ai_task_id`: AI DB의 공고 분석 작업을 논리 참조

#### `posting_competencies`

공고와 요구 역량의 다대다 연결 테이블이다.

- PK: `(job_posting_id, competency_id)`
- `weight`: 공고에서 해당 역량의 중요도, 0 이상 1 이하
- `evidence_line`, offset: AI가 역량을 판단한 공고 본문 근거

#### `job_posting_questions`

공고 상세 화면에서 보여 줄 자기소개서 문항을 저장한다. `applications` 없이 공고에 직접 속한다.

- `job_posting_id → job_postings.id`
- `sequence`, `prompt_text`, 글자/바이트 제한
- `is_active=false`: 재수집으로 교체된 과거 문항을 기존 답변과 함께 보존
- 활성 행에만 `(job_posting_id, sequence)` UNIQUE 적용

따라서 공고 상세 문항을 저장할 테이블이 없는 상태가 아니다. 현재는 이 테이블이 그 역할을 한다.

### 3.5 사용자별 공고 상태와 매칭

#### `bookmarks`

사용자와 공고의 북마크 관계다.

- PK: `(user_id, job_posting_id)`

#### `job_matches`

사용자 경험 역량과 공고 요구 역량을 비교한 최신 결과 한 건을 저장한다.

- UNIQUE: `(user_id, job_posting_id)`
- `match_score`: 0 이상 100 이하
- `coverage`: 역량별 점수와 근거 경험
- `actions`: 부족한 역량을 보완하기 위한 제안
- `input_hash`: 경험이나 공고 역량 변경 여부 확인
- `source_ai_task_id`: AI DB MATCH 작업 논리 참조

`assessment.application_id` 방식은 사용하지 않는다. 매칭의 실제 단위가 지원서가 아니라
`로그인 사용자 + 공고`이기 때문이다.

### 3.6 자기소개서 현재 답변

#### `cover_letter_answers`

사용자별·공고 문항별 현재 답변 한 건만 저장한다.

- UNIQUE: `(user_id, job_posting_question_id)`
- 최초 저장: INSERT
- 이후 저장: 같은 행 UPDATE
- 버전 번호와 이전 답변 이력은 저장하지 않는다.
- `source`: `MANUAL`, `AI_GENERATED`, `AI_ASSISTED`

#### `answer_experiences`

답변 또는 AI 초안 생성에 사용한 경험들을 연결한다.

- PK: `(answer_id, experience_id)`
- 답변 삭제 시 연결도 삭제
- 사용 중인 경험은 함부로 삭제하지 못하도록 경험 FK는 `RESTRICT`

#### `answer_requirement_results`

문항의 세부 요구사항을 답변이 충족했는지 저장하려는 테이블이다.

현재 목업 화면과 API 응답에서는 이 기능이 확인되지 않았다. MVP에서 요구사항별 충족 표시를 만들지 않는다면
이 테이블과 `job_posting_questions.requirements`는 제거 후보이며, 팀 결정이 필요하다.

## 4. AI Service DB ERD — 2개 테이블

### `ai_tasks`

경험 입력 정리, 공고 분석, 매칭, 자기소개서 초안처럼 오래 걸리는 AI 작업의 상태를 저장한다.

- Career DB와 별도 PostgreSQL DB 또는 별도 서비스 소유 DB
- `PENDING → RUNNING → SUCCEEDED/FAILED/CANCELLED`
- 요청 시 task ID를 반환하고 프론트는 상태 조회 API를 호출한다.
- `user_id`, `job_posting_id`, `question_id` 등은 Career DB ID의 논리 참조다.
- 서로 다른 DB이므로 물리 FK를 걸지 않는다.
- Kafka를 사용하지 않아도 HTTP 요청 + 작업 테이블 + 백그라운드 실행으로 비동기를 구현할 수 있다.

### `ai_task_attempts`

AI 호출 재시도 횟수와 각 시도의 성공·실패 이력을 저장한다.

- `ai_task_id → ai_tasks.id`
- AI 작업 삭제 시 시도 이력도 함께 삭제

AI DB는 DBML과 제약 SQL은 있지만 Career Backend처럼 실제 Flyway migration으로 연결된 상태는 아니다.
AI 서버 저장소에서 별도 Flyway 이력으로 구성해야 한다.

## 5. 제거된 테이블

| 제거 대상 | 제거 이유 |
|---|---|
| `applications` | 최종 화면에 지원서 생성 단계가 없고 매칭 단위도 user + posting임 |
| 답변 버전 테이블 | 이전 버전 보기 기능을 삭제하고 현재 답변 한 건만 저장함 |
| `outbox_events` | Kafka/메시지 브로커를 사용하지 않는 현재 팀 범위에 과함 |
| Career DB의 `async_tasks` | AI Service DB의 `ai_tasks`로 작업 상태 소유권을 분리함 |

## 6. 최종 권장 API — 총 20개

### 6.1 Auth — 4개, 실제 구현 완료

| Method | URL | 주요 처리 |
|---|---|---|
| GET | `/api/auth/slack/start` | Slack 로그인 시작 |
| GET | `/api/auth/slack/callback` | Slack 사용자 upsert, 세션 생성 |
| GET | `/api/auth/me` | 현재 로그인 사용자 조회 |
| POST | `/api/auth/logout` | 세션 종료 |

`/api/auth/me`는 현재 구현상 미로그인일 때 `200 OK`와 `null`을 반환한다.

### 6.2 Competency — 1개, 미구현

| Method | URL | 연결 테이블 |
|---|---|---|
| GET | `/api/competencies` | `competencies`, `competency_aliases` |

### 6.3 Posting — 5개, 미구현

| Method | URL | 연결 테이블 |
|---|---|---|
| GET | `/api/postings` | `job_postings`, `companies`, `bookmarks`, `job_matches`, 답변 진행 수 |
| GET | `/api/postings/{postingId}` | `job_postings`, `posting_competencies`, `job_matches` |
| GET | `/api/postings/{postingId}/questions` | `job_posting_questions`, `cover_letter_answers` |
| GET | `/api/postings/{postingId}/match` | `job_matches` |
| PUT | `/api/postings/{postingId}/bookmark` | `bookmarks` upsert/delete 또는 상태 변경 |

### 6.4 Experience — 4개, 미구현

| Method | URL | 연결 테이블 |
|---|---|---|
| GET | `/api/experiences` | `experiences`, `experience_competencies` |
| POST | `/api/experiences` | 경험과 역량 연결 생성 |
| PUT | `/api/experiences/{experienceId}` | 본인 경험과 역량 연결 수정 |
| POST | `/api/experience-intakes` | AI 경험 정리 작업 생성 |

여러 역량 필터는 `competencyId[]=1`이 아니라 다음처럼 같은 이름을 반복한다.

```http
GET /api/experiences?competencyId=1&competencyId=3&sort=latest&size=20
```

### 6.5 Question/Answer — 3개, 미구현

| Method | URL | 연결 테이블 |
|---|---|---|
| POST | `/api/questions/{questionId}/drafts` | AI DRAFT 작업 생성, 선택 경험 전달 |
| GET | `/api/questions/{questionId}/answer` | 현재 답변 한 건 조회 |
| PUT | `/api/questions/{questionId}/answer` | 현재 답변 INSERT 또는 UPDATE |

별도 답변 등록 버튼과 버전 API는 없다. 화면의 저장 버튼이 PUT API를 호출하고 성공하면 `저장됨`을 표시한다.

### 6.6 AI Task — 1개, 미구현

| Method | URL | 연결 테이블 |
|---|---|---|
| GET | `/api/ai-tasks/{taskId}` | AI 서버의 `ai_tasks` 상태 조회 또는 Career Backend 프록시 |

### 6.7 Internal — 2개, 미구현

| Method | URL | 용도 |
|---|---|---|
| POST | `/internal/mock/competency-extractions` | 개발용 AI 역량 추출 mock. 운영 비활성화 |
| PUT | `/internal/postings/{postingId}/competencies` | AI 분석 결과로 공고 역량 갱신 |

개발용 mock을 운영에서 제외하면 노출 API는 19개다. AI 서버와 Career Backend 사이의 실제 호출 방향이
확정되면 두 internal API의 인증 방식과 요청 주체를 상세 명세에 반드시 적어야 한다.

## 7. 삭제하거나 변경해야 할 기존 API

| 기존 API | 최종 처리 | 이유 |
|---|---|---|
| `GET /api/me` | `/api/auth/me`로 변경 | 실제 인증 컨트롤러와 통일 |
| `/api/postings/{id}/application-questions` | `/api/postings/{id}/questions`로 변경 | applications 제거 |
| `/api/posting-questions/{id}/...` | `/api/questions/{id}/...`로 변경 | 자원 명칭 단순화 |
| `GET /api/applications/{id}/assessment` | `/api/postings/{id}/match`로 변경 | 매칭 단위가 user + posting |
| `POST /api/applications` | 삭제 | 지원서 생성 흐름 없음 |
| `POST /api/applications/{id}/questions` | 삭제 | 문항은 공고에 이미 속함 |
| `/api/answers/{id}/versions` | 삭제 | 버전 기능 제거 |
| `/api/answers/{answerId}/drafts` | question 기반 URL로 변경 | 초안 생성 전에 answer ID가 없을 수 있음 |
| `GET /api/mock-jobs/{jobId}` | `/api/ai-tasks/{taskId}`로 변경 | 작업 상태 자원으로 명확화 |
| 공개 `PUT /api/postings/{id}/competencies` | `/internal/...`로 이동 | 사용자가 공용 공고 역량을 변경하면 안 됨 |

## 8. 응답 및 상태 코드 기준

- 생성된 비동기 작업: `202 Accepted`와 `taskId`
- 일반 조회 성공: `200 OK`
- 답변 저장 성공: `200 OK`
- 로그아웃 성공: `204 No Content`
- 요청 형식 오류: `400 Bad Request`
- 미로그인 보호 API: `401 Unauthorized`
- 다른 사용자의 데이터 접근: `403 Forbidden`
- 대상 없음: `404 Not Found`
- 같은 초안 작업 중복 실행: `409 Conflict`

현재 Java 컨벤션의 오류 응답은 다음과 같다.

```json
{
  "message": "경험을 찾을 수 없습니다."
}
```

Notion에 `EXPERIENCE_NOT_FOUND` 같은 오류 코드 필드를 적을 것이라면 실제 응답도
`{"code":"...","message":"..."}`로 바꾸고 모든 API에 일관되게 적용해야 한다.

## 9. ERD와 API 매칭 최종 판단

최종 권장 API 기준으로는 핵심 관계가 맞는다.

- 경험 등록 API ↔ `experiences`, `experience_competencies`
- 공고 목록/상세 API ↔ `job_postings`, `posting_competencies`, `bookmarks`
- 공고 문항 API ↔ `job_posting_questions`
- 사용자·공고 매칭 API ↔ `job_matches(user_id, job_posting_id)`
- 문항별 현재 답변 API ↔ `cover_letter_answers(user_id, question_id)`
- 비동기 상태 조회 API ↔ AI DB `ai_tasks`

다만 다음 세 가지는 아직 최종 확정이 필요하다.

1. `answer_requirement_results` 기능을 MVP에서 실제 사용할지 결정
2. AI DB용 Flyway migration과 AI 서버 구현 추가
3. Notion 각 API 상세 페이지의 Path/Query, Request, Response, 상태 코드, 오류 코드를 이 구조로 수정

## 10. 실제 파일 위치

- Career ERD: `codex/20260903-1333/career_fit_final_slack.dbml`
- AI ERD: `codex/20260903-1333/ai_service_final.dbml`
- Career DB Flyway V1: `backend/src/main/resources/db/migration/V1__create_career_fit_schema.sql`
- Spring DB 설정: `backend/src/main/resources/application.yml`
- 기존 API 수정 가이드: `codex/20260903-1333/api-url-and-ddl-auto-final-review.md`
- H2 및 DB 정리 결과: `codex/20260903-1347/h2-removal-database-cleanup-and-review-handoff.md`
