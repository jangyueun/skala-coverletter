# ERD v1 → v2 변경 요약 (2026-09-03)

1차 리뷰(동료 시니어 개발자) + 목업 파일(render.js/data.js/DESIGN.md/README.md) 대조 검증에서 나온
P0~P2 이슈를 전부 반영한 버전입니다. 파일 구성은 다음과 같습니다.

- `career_fit.dbml` — Career Backend DB 물리 ERD
- `ai_service.dbml` — AI Service DB 물리 ERD (신규 분리)
- `career_fit_constraints.sql` — dbdiagram DDL 적용 후 실행할 CHECK/부분 유니크/트리거
- `ai_service_constraints.sql` — 위와 동일, AI Service DB용

## P0 — 구현 blocking 이슈 해결

| # | v1 문제 | v2 조치 |
|---|---|---|
| 1 | AI 서버 DB 분리 전제와 물리 FK 불일치 | `ai_service.dbml`로 완전 분리. Career DB 쪽 참조 컬럼은 `source_ai_task_id`(FK 없음)로, AI DB 쪽은 `external_user_id/company_id/job_posting_id/question_id`(FK 없음)로 명명 |
| 2 | job_postings ↔ async_tasks, experiences ↔ async_tasks 순환 FK (DB 미분리 시에도 존재) | DB 분리로 자동 해소. 순환 참조 자체가 사라짐 |
| 3 | applications.job_posting_id NOT NULL vs 목업 postingId:null 3건 | NOT NULL 유지 + "수동 지원은 MANUAL job_posting을 먼저 생성"을 스키마 note와 서비스 레이어 규칙으로 명문화. **목업 데이터(id 106/107/108)는 마이그레이션 시 MANUAL companies/job_postings row로 백필 필요** |
| 4 | async_tasks (user_id, task_type, input_hash) UNIQUE — NULL 우회로 멱등성 무력화 | `ai_tasks.idempotency_key`(NOT NULL, 단일 컬럼 UNIQUE)로 대체. NULL이 끼어들 여지 자체를 제거 |

## P1 — 무결성 제약 추가

| # | 항목 | 위치 |
|---|---|---|
| 5 | strength/weight/match_score 범위, version/length_limit/char_count/byte_count/retry_count 부호 | `career_fit_constraints.sql`, `ai_service_constraints.sql` (DBML은 컬럼 CHECK를 표현 못해 SQL로 분리) |
| 6 | 문항당 최종 답변 하나 | `CREATE UNIQUE INDEX uq_answer_final ... WHERE is_final = true` |
| 7 | competency_aliases 정책 | **별칭 1개 = 역량 1개**로 확정, `alias UNIQUE` 적용 (동음이의 다중 매핑 필요 시 재논의 필요 — 아래 "확인 필요" 참고) |
| 8 | MATCH 작업 단위 | **공고 1건당 task 1개**로 확정. `ai_tasks` 인덱스에 `(task_type, external_job_posting_id, status)` 추가, 재계산 상태 조회 시 external_job_posting_id 조건 필수라고 note에 명시 |

## P2 — 설계 보완

| # | 항목 | 조치 |
|---|---|---|
| 9 | job_postings 상태 컬럼 부재 (active=true API가 이미 존재) | `status posting_status` (ACTIVE/CLOSED/DELETED/COLLECT_FAILED) 추가 |
| 10 | application_status의 RESULT가 합/불을 표현 못함 | CANCELLED, DOCUMENT_PASSED/FAILED, INTERVIEWING, FINAL_PASSED/FAILED로 세분화 |
| 11 | async_tasks 대상 FK 전부 CASCADE → 이력 보존 목적과 충돌 | DB 분리로 물리 FK 자체가 없어져 해소. AI DB 내부 이력은 `ai_task_attempts`로 별도 보존 |
| 12 | questions_from_server boolean이 "미확인"과 "확인했으나 없음"을 구분 못함 | `posting_questions_status` enum (NOT_CHECKED/AVAILABLE/NOT_AVAILABLE/FAILED)로 전환 |

## 추가로 반영한 것 (1차 리뷰에 없었지만 대조 중 발견)

- **companies.normalized_name 추가**: name UNIQUE만으로는 "(주)세움테크" vs "세움테크" 같은 표기 차이를 못 잡음.
- **posting_competencies.evidence_line 보강**: start/end offset, section, extractor_version 컬럼 추가 — 검증·UI 표시용.
- **cover_letter_questions.requirements**: `addressed`를 문항 JSONB에서 분리해 `answer_requirement_results` 테이블로 — 답변 버전별 충족 여부를 독립적으로 표현.
- **answer_experiences 삭제 정책**: CASCADE → RESTRICT로 변경 (경험 삭제 시 과거 답변의 근거 이력 보존). **주의: 이제 사용자가 근거로 쓰인 경험을 삭제하려 하면 에러가 남 → 경험 소프트 삭제(`deleted_at`) 구현이 사실상 필수로 딸려옴.**
- **outbox_events 테이블 신규**: Career DB 커밋과 이벤트 발행의 원자성을 보장하기 위한 Outbox 패턴. 1차 리뷰의 권장 흐름("Outbox 이벤트 저장 → 브로커 → AI DB")을 스키마로 구현.
- **job_match_history 테이블 신규**: 모델/프롬프트 변경 전후 결과 비교용 (1차 리뷰 지적 반영).
- **timestamp → timestamptz** 전체 컬럼 일괄 변경.
- **updated_at 자동 갱신**: JPA Auditing 미사용 시를 대비한 트리거를 `career_fit_constraints.sql`에 포함 (JPA Auditing 쓸 경우 이 트리거는 빼야 함 — 둘 다 켜면 충돌).

## 다른 시니어 개발자에게 컨펌받아야 할 결정 사항 (스키마만으로는 확정 불가)

1. **competency_aliases 정책**: "별칭 1개 = 역량 1개"로 확정했는데, 실제로 문맥에 따라 하나의 용어가 여러 역량으로 해석돼야 하는 케이스가 있는지 도메인 관점에서 재확인 필요.
2. **answer_experiences RESTRICT 채택**에 따른 경험 소프트 삭제 구현 여부 — 이건 스키마 변경이 아니라 API/서비스 레이어 작업이 추가로 필요함을 의미.
3. **applications 마이그레이션**: 목업의 postingId:null 3건을 실제로 MANUAL 공고로 백필하는 스크립트를 누가/언제 작성할지.
4. **outbox_events 도입 범위**: 모든 도메인 이벤트에 적용할지, MATCH/DRAFT 트리거용으로만 좁힐지.
5. **AI Service DB를 정말 별도 물리 DB로 운영할지, 아니면 Day 2~3 일정상 같은 DB에 스키마만 분리(별도 schema)해 둘지** — 후자라면 물리 FK 부재 원칙은 그대로 지키되 인프라는 나중에 분리하는 절충안도 가능.

## 아직 DB 스키마 범위 밖 (별도로 처리 필요, 1차 리뷰 5번 항목)

- render.js/data.js/DESIGN.md/README.md를 이 v2 ERD 기준으로 통일 (assessment→job_matches, aliases[]→competency_aliases, ai_job/crawl_job→ai_tasks, AX-2 상태코드, README API 개수 14→18)
- 이 부분은 DB 설계 승인과 별개로 프론트/문서 트랙에서 진행 가능.

## 실제 검증 방법 (주장이 아니라 실행 결과)

이론 검토로 끝내지 않고 실제 PostgreSQL 16에 두 DB를 만들어 끝까지 돌렸습니다.

1. `@dbml/core`(dbdiagram.io가 쓰는 것과 같은 파서)로 `career_fit.dbml` / `ai_service.dbml`을 파싱 → PostgreSQL DDL로 export. 문법 오류 없이 통과.
2. 로컬 PostgreSQL 16에 `career_fit_test`, `ai_service_test` 두 DB를 만들고 위 DDL + `*_constraints.sql`을 순서대로 실행 → **ON_ERROR_STOP=1 기준으로 에러 0건, 두 DB 모두 스키마 생성 완료.**
3. `smoke_test.sql` / `smoke_test_ai.sql`로 아래 10개 케이스를 실제 INSERT로 검증 — **전부 PASS**:
   - competency_aliases 중복 alias 차단
   - experience_competencies.strength 범위(0~1) CHECK
   - applications (user_id, job_posting_id) 중복 지원 차단
   - applications 상태-submitted_at 정합성 CHECK
   - cover_letter_answers 문항당 is_final=true 하나만 허용 (부분 유니크 인덱스)
   - ai_tasks: **external_user_id가 NULL인 CRAWL 작업도 idempotency_key로 멱등성 보장** (v1의 핵심 결함이었던 지점)
   - ai_tasks: MATCH인데 external_job_posting_id 없으면 CHECK로 차단
   - ai_tasks: 정상 MATCH 작업 삽입 성공
   - ai_tasks.retry_count 음수 차단

`smoke_test.sql`/`smoke_test_ai.sql`을 그대로 받아서 재실행하면 동일한 결과를 재현할 수 있습니다 — 다른 시니어 개발자분이 컨펌하실 때 "말로만 맞다"가 아니라 직접 돌려서 확인 가능합니다.
