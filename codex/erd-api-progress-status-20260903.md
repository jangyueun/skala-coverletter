# Career Fit ERD·API 명세 수정 진행현황

작성일: 2026-09-03 14:28 KST  
작성 기준: 목업 최종 화면, Slack OAuth 구현, Career/AI DB 분리안, 기존 Notion API 명세 검토 결과

## 1. 현재 결론

ERD의 핵심 구조와 API URL 수정 방향은 정리됐다. Career DB는 실제 Flyway 최초 마이그레이션까지 작성하고
PostgreSQL에서 검증했다. 그러나 Notion API 상세 명세 수정과 인증 외 기능 API 구현은 아직 완료되지 않았다.

| 영역 | 상태 | 현재 위치 |
|---|---|---|
| 화면·기능 흐름 분석 | 완료 | 목업 및 최종 실행 화면 기준으로 확정 |
| Career DB ERD | 수정안 완료 | 15개 테이블 |
| AI Service DB ERD | 수정안 완료 | 2개 테이블 |
| Career DB DDL | 작성·검증 완료 | Flyway V1 |
| AI Service DB DDL 적용 | 미완료 | DBML/제약 SQL만 있고 AI 서버 Flyway 미연결 |
| API URL 수정안 | 완료 | Auth 포함 20개 |
| Notion API 상세 페이지 수정 | 미완료 | 담당자가 반영해야 함 |
| Java API 구현 | 일부 완료 | Slack 인증 API 4개만 구현 |
| PostgreSQL 연결 설정 | 완료 | H2 제거, PostgreSQL JDBC + Flyway |
| 통합 기능 테스트 | 미완료 | 도메인 API 구현 후 가능 |

## 2. 최종 확정한 서비스 흐름

```text
Slack 로그인
  → 사용자 생성/조회

경험 등록
  → 경험 내용 저장
  → 경험별 역량 태그와 strength 저장

공고 등록/수집
  → 회사와 공고 저장
  → 공고 요구 역량과 자기소개서 문항 저장

사용자·공고 AI 매칭
  → 별도 AI DB에 비동기 작업 생성
  → 사용자는 다른 화면 이용 가능
  → 완료 결과를 user + posting 기준으로 Career DB에 저장

공고 문항별 자기소개서 작성
  → 경험 선택
  → AI 초안 비동기 생성
  → 사용자가 수정 후 저장
  → 문항별 현재 답변 1건만 유지
```

다음 기능은 최종 화면에서 제거된 것으로 반영했다.

- 지원서 만들기 버튼
- `applications` 중심 흐름
- 자기소개서 이전 버전 조회
- 답변 버전 테이블 및 버전 API
- Kafka/outbox 기반 메시징

Kafka 없이 HTTP 호출, AI 작업 테이블, 백그라운드 처리, 상태 조회 방식으로 비동기를 구성한다.

## 3. ERD 수정 진행현황

### 3.1 Career DB — 15개 테이블

| 그룹 | 테이블 | 역할 | 상태 |
|---|---|---|---|
| 사용자 | `users` | Slack 사용자 | 확정 |
| 역량 | `competencies` | 표준 역량 | 확정 |
| 역량 | `competency_aliases` | 표준 역량 별칭 | 확정 |
| 역량 | `competency_candidates` | AI 추출 미확정 역량 검수 | 유지, 관리자 기능은 후순위 |
| 경험 | `experiences` | 사용자 STAR 경험 | 확정 |
| 경험 | `experience_competencies` | 경험과 역량의 다대다 관계 | 확정 |
| 공고 | `companies` | 회사 | 확정 |
| 공고 | `job_postings` | 채용공고 | 확정 |
| 공고 | `posting_competencies` | 공고 요구 역량과 중요도 | 확정 |
| 공고 | `job_posting_questions` | 공고 자기소개서 문항 | 확정 |
| 사용자·공고 | `bookmarks` | 북마크 | 확정 |
| 사용자·공고 | `job_matches` | 사용자·공고 최신 매칭 결과 | 확정 |
| 자기소개서 | `cover_letter_answers` | 사용자·문항별 현재 답변 | 확정 |
| 자기소개서 | `answer_experiences` | 답변 작성에 사용한 경험 | 확정 |
| 자기소개서 | `answer_requirement_results` | 요구사항별 충족 여부 | 팀 결정 필요 |

### 3.2 핵심 키와 관계

- 사용자 식별: `users(slack_team_id, slack_user_id)` UNIQUE
- 경험 소유: `experiences.user_id → users.id`
- 경험 역량: `experience_competencies(experience_id, competency_id)` PK
- 공고 소유 회사: `job_postings.company_id → companies.id`
- 공고 요구 역량: `posting_competencies(job_posting_id, competency_id)` PK
- 공고 문항: `job_posting_questions.job_posting_id → job_postings.id`
- 북마크: `bookmarks(user_id, job_posting_id)` PK
- 매칭: `job_matches(user_id, job_posting_id)` UNIQUE
- 현재 답변: `cover_letter_answers(user_id, job_posting_question_id)` UNIQUE
- 답변 경험: `answer_experiences(answer_id, experience_id)` PK

### 3.3 ERD에서 해결한 기존 문제

1. `assessment.application_id`를 제거하고 매칭을 `user_id + job_posting_id` 기준으로 변경했다.
2. 목업의 `postingId=NULL` 지원 이력을 맞추기 위해 `applications`를 억지로 유지하지 않고 지원서 생성 기능 자체를 제거했다.
3. 역량 별칭 배열을 `competency_aliases`의 별도 행 구조로 정규화했다.
4. 자기소개서 버전 테이블을 제거하고 현재 답변 한 건만 저장하도록 변경했다.
5. 경험별 역량 강도를 `experience_competencies.strength`에 저장한다.
6. 공고 상태와 문항 수집 상태를 명시적으로 저장한다.
7. 공고 문항은 `applications`가 아니라 `job_postings`에 직접 연결했다.
8. AI 작업 ID는 서로 다른 DB 사이에서 값만 저장하고 물리 FK를 제거했다.
9. `job_posting_questions`는 과거 문항을 `is_active=false`로 보존하면서 활성 문항끼리만 순번 중복을 막는다.

### 3.4 AI Service DB — 2개 테이블

| 테이블 | 역할 | 상태 |
|---|---|---|
| `ai_tasks` | 비동기 AI 작업 상태, 입력, 결과, 대상 ID 저장 | DBML 확정 |
| `ai_task_attempts` | AI 호출별 재시도 이력 | DBML 확정 |

Career DB와 AI DB는 물리적으로 분리하며 FK를 걸지 않는다. Career DB의 `source_ai_task_id`와
AI DB의 `user_id`, `job_posting_id`, `question_id` 등은 논리 참조다.

MATCH 작업은 실행 중 하나만 허용하는 단순 유니크 제약을 두지 않았다. 매칭 중 경험이 변경되면 새 입력이
누락될 수 있기 때문이다. 결과 반영 전에 `input_hash`가 현재 입력과 일치하는지 검사해야 한다.

## 4. DB 구현 진행현황

### 완료

- H2 의존성과 실행 설정 제거
- 제공받은 PostgreSQL에 JDBC로 직접 연결하도록 설정
- Hibernate `ddl-auto:update` 제거
- Hibernate `ddl-auto:validate` 적용
- Flyway starter와 PostgreSQL 모듈 추가
- Career DB 최초 마이그레이션 작성
- CHECK, FK, 인덱스, 활성 문항 부분 UNIQUE, `updated_at` 트리거를 V1에 포함
- Java enum 호환을 위해 DB에는 PostgreSQL native enum 대신 `varchar + CHECK` 사용
- `clean-disabled:true`, `baseline-on-migrate:false` 안전 설정

### 검증

- Gradle 전체 테스트 통과
- PostgreSQL 17 빈 임시 DB에서 Flyway V1 적용 성공
- Flyway 적용 후 Hibernate schema validation 성공
- 검증용 임시 DB 삭제 완료
- 공유 원격 DB에는 삭제 또는 변경 명령을 실행하지 않음

### 현재 DB 상태

로컬 PostgreSQL에 Career Fit 또는 이전 검증용으로 식별되는 DB는 남아 있지 않았다.
`school_db`, `skala_db`, `postgres`의 별도 실습 스키마는 다른 프로젝트 데이터라 삭제하지 않았다.

### DB 기준 파일

- ERD: `codex/20260903-1333/career_fit_final_slack.dbml`
- Flyway: `backend/src/main/resources/db/migration/V1__create_career_fit_schema.sql`
- 설정: `backend/src/main/resources/application.yml`

실제 DB 적용 기준은 Flyway다. DBML export SQL과 constraints SQL을 별도로 중복 적용하면 안 된다.

## 5. API 명세 수정 진행현황

### 5.1 실제 구현 완료 API — 4개

| Method | URL | 상태 |
|---|---|---|
| GET | `/api/auth/slack/start` | 구현 완료 |
| GET | `/api/auth/slack/callback` | 구현 완료 |
| GET | `/api/auth/me` | 구현 완료 |
| POST | `/api/auth/logout` | 구현 완료 |

현재는 Slack OAuth와 서버 세션 방식이다. 향후 Spring Security JWT로 변경해도 JWT subject에 내부
`users.id`를 사용하면 ERD를 바꿀 필요가 없다. 로컬 이메일·비밀번호 로그인을 추가할 때만 인증 테이블을 재검토한다.

### 5.2 최종 권장 API — 아직 구현 전

#### Competency — 1개

```http
GET /api/competencies
```

#### Posting — 5개

```http
GET /api/postings
GET /api/postings/{postingId}
GET /api/postings/{postingId}/questions
GET /api/postings/{postingId}/match
PUT /api/postings/{postingId}/bookmark
```

북마크 PUT은 토글로 구현하지 않고 요청 Body에 최종 상태를 받는 방식이 안전하다.

```json
{
  "bookmarked": true
}
```

#### Experience — 4개

```http
GET  /api/experiences
POST /api/experiences
PUT  /api/experiences/{experienceId}
POST /api/experience-intakes
```

#### Question/Answer — 3개

```http
POST /api/questions/{questionId}/drafts
GET  /api/questions/{questionId}/answer
PUT  /api/questions/{questionId}/answer
```

#### AI Task — 1개

```http
GET /api/ai-tasks/{taskId}
```

#### Internal — 2개

```http
POST /internal/mock/competency-extractions
PUT  /internal/postings/{postingId}/competencies
```

최종 권장 API는 Auth 포함 20개다. 개발용 mock endpoint를 운영에서 제외하면 운영 노출 대상은 19개다.

### 5.3 기존 API에서 변경하거나 삭제할 항목

| 기존 명세 | 최종 수정 | 상태 |
|---|---|---|
| `GET /api/me` | `GET /api/auth/me` | 수정안 확정, Java 구현 완료 |
| `/api/postings/{id}/application-questions` | `/api/postings/{id}/questions` | Notion 수정 필요 |
| `/api/posting-questions/{id}/...` | `/api/questions/{id}/...` | Notion 수정 필요 |
| `GET /api/applications/{id}/assessment` | `GET /api/postings/{id}/match` | Notion 수정 필요 |
| `POST /api/applications` | 삭제 | Notion 수정 필요 |
| `POST /api/applications/{id}/questions` | 삭제 | Notion 수정 필요 |
| `/api/answers/{id}/versions` | 삭제 | Notion 수정 필요 |
| `/api/answers/{answerId}/drafts` | `/api/questions/{questionId}/drafts` | Notion 수정 필요 |
| `GET /api/mock-jobs/{jobId}` | `GET /api/ai-tasks/{taskId}` | Notion 수정 필요 |
| 공개 공고 역량 PUT | `/internal/postings/{id}/competencies` | Notion 수정 필요 |

### 5.4 API 문서 공통 규칙

- 비동기 작업 생성: `202 Accepted`와 `taskId`
- 일반 조회/저장: `200 OK`
- 로그아웃: `204 No Content`
- 검증 오류: `400 Bad Request`
- 미로그인: `401 Unauthorized`
- 소유권 위반: `403 Forbidden`
- 대상 없음: `404 Not Found`
- 같은 초안 작업 실행 중 중복 요청: `409 Conflict`
- 여러 역량 Query Parameter: `?competencyId=1&competencyId=3`
- 성공 응답: 의미 있는 DTO 직접 반환
- 현재 오류 응답: `{"message":"..."}`

Notion에 문자열 오류 코드를 제공하려면 Java 컨벤션과 실제 구현을 모두 다음 형태로 함께 바꿔야 한다.

```json
{
  "code": "EXPERIENCE_NOT_FOUND",
  "message": "경험을 찾을 수 없습니다."
}
```

## 6. 목업과의 정합성

목업 HTML은 데모이므로 DB 테이블 구조와 동일하게 다시 만들 필요는 없다. 실제 기능 흐름만 다음 기준으로 맞으면 된다.

- 경험 등록 시 역량 ID와 strength를 API로 전달
- 공고별 matchScore는 `job_matches`에서 조회
- 공고 문항은 `job_posting_questions`에서 조회
- 자기소개서는 문항별 현재 답변 한 건으로 조회·저장
- AI 작업 중에는 화면을 떠나도 되며 task ID로 상태를 다시 조회
- `applications`, 답변 버전, 지원서 생성 버튼은 사용하지 않음

목업의 과거 하드코딩 데이터는 실제 API DTO로 교체할 때 정리하면 된다.

## 7. 아직 결정하거나 구현해야 할 사항

### P0 — 다음 작업 전에 팀 합의 필요

1. `answer_requirement_results`와 문항 `requirements`를 MVP에서 사용할지 결정
2. Notion API 상세 페이지의 URL, Parameter, Request, Response, 상태 코드, 오류 코드 수정
3. 기존 원격 PostgreSQL에 데이터가 있는지 확인하고 V1 적용 방식 결정

### P1 — 구현 필요

4. 인증 외 16개 API 구현
5. AI 서버용 Flyway migration 작성
6. Career Backend와 AI 서버 간 인증 및 내부 API 호출 방향 확정
7. MATCH 결과 반영 시 현재 `input_hash` 검증 구현
8. 비동기 초안 중복 요청의 `409 DRAFT_ALREADY_RUNNING` 보장 구현

### P2 — 테스트 필요

9. 사용자 소유권 테스트
10. 공고·경험·매칭·답변 API 통합 테스트
11. AI 작업 성공·실패·재시도·오래 걸리는 작업의 polling 테스트
12. Notion 예시 응답과 실제 JSON 응답 계약 테스트

## 8. 담당자별 다음 작업

### ERD/DB 담당자

- `answer_requirement_results` 유지 여부를 팀에 확인
- 유지 시 API 응답에 요구사항 충족 결과를 명시
- 제거 시 적용 전이면 최종 V1 정리, 이미 적용됐다면 V2로 삭제
- AI 서버 저장소에 별도 Flyway 설정 추가

### API 담당자

- Notion 20개 API의 상세 페이지를 최종 URL로 정리
- 각 API에 Path·Query Parameter, Request Body, Response Body, HTTP 상태 코드, 오류 코드를 모두 작성
- Auth 4개 외 기능 API 구현
- 오류 응답에 `code`를 넣을지 팀과 확정

### 목업/프론트 담당자

- `applications`와 버전 ID를 전제로 한 호출 제거
- 경험 등록 요청에 역량 ID와 strength 포함
- 비동기 작업 생성 후 `taskId` polling 처리
- 답변 저장 성공 시 `저장됨` 표시
- 세션 쿠키 요청에 `credentials: 'include'` 적용

## 9. 문서 및 파일 목록

| 파일 | 용도 |
|---|---|
| `codex/20260903-1333/career_fit_final_slack.dbml` | Career DB 최종 ERD 수정안 |
| `codex/20260903-1333/ai_service_final.dbml` | AI Service DB 최종 ERD 수정안 |
| `backend/src/main/resources/db/migration/V1__create_career_fit_schema.sql` | 실제 Career DB 최초 마이그레이션 |
| `codex/20260903-1333/api-url-and-ddl-auto-final-review.md` | API URL 및 DB 설정 수정 근거 |
| `codex/20260903-1340/ddl-auto-flyway-correction.md` | ddl-auto 정정 및 Flyway 검증 |
| `codex/20260903-1347/h2-removal-database-cleanup-and-review-handoff.md` | H2·기존 DB 확인 결과 |
| `codex/20260903-1419/current-erd-and-api-spec-summary.md` | 현재 ERD/API 전체 설명 |
| `codex/20260903-1428/erd-api-progress-status-20260903.md` | 이 진행현황 통합 문서 |

## 10. 최종 진행 판단

ERD 방향은 현재 기능 흐름과 일치하며 Career DB는 실행 가능한 Flyway V1까지 준비됐다. API는 URL과 자원 경계는
정리됐지만 Notion 상세 명세와 실제 Java 구현은 인증 영역을 제외하면 아직 시작 전이다.

따라서 현재 단계를 한 문장으로 표현하면 다음과 같다.

> 데이터 모델과 API 설계 수정안은 준비됐고 Career DB 기술 검증도 끝났지만, Notion 계약 확정과 기능 API 구현은 남아 있다.
