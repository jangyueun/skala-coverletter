# 최종 ERD/API 리뷰 결과 및 적용한 수정 (2026-09-03)

검토 대상: `career_fit_final.dbml`, `ai_service_final.dbml`, `api-spec-final-change-guide.md` (2차 전달본)

## 총평

방향 자체는 아주 좋습니다. `applications`/답변 버전/`outbox_events`를 통째로 들어낸 건 "최종 화면에 그 개념이 없다"는 제품 결정을 정확히 스키마에 반영한 것이고, 이전 리뷰에서 P0로 지적했던 문제(지원서-공고 postingId null, 순환 FK 등)는 애초에 그 기능이 없어지면서 자연스럽게 사라졌습니다. `job_posting_questions.is_active`로 hard delete 대신 비활성화를 택해 답변 이력을 보존한 것, `cover_letter_answers`를 `(user_id, job_posting_question_id)` 유니크 한 행으로 바꾼 것, Supabase Auth 연결도 전부 합리적입니다.

다만 **실제로 로컬 PostgreSQL 16에 두 DB를 빌드해서 돌려본 결과, 파싱 자체가 안 되는 치명적 오류가 있었습니다.** 그리고 그것과 별개로 놓친 부분 몇 개를 찾아 같이 고쳤습니다.

## 🔴 치명적 — 다이어그램 자체가 파싱되지 않음 (수정 완료)

원본 두 파일 모두 테이블마다 다음과 같은 `checks { ... }` 블록으로 CHECK 제약을 넣어뒀습니다.

```dbml
checks {
  `strength >= 0 AND strength <= 1` [name: 'ck_experience_competency_strength']
}
```

dbdiagram.io가 실제로 쓰는 파서(`@dbml/core`, 2026-09-03 기준 최신 배포 버전 10.1.1 — npm에 이보다 새 버전 없음)로 직접 파싱을 시도해봤는데 **둘 다 SyntaxError로 실패**합니다.

```
SyntaxError: Expected "note", "note:", "}", comment, indexes, or whitespace but "c" found.
  career_fit_final.dbml:159  (checks 블록 시작 지점)
  ai_service_final.dbml:57   (checks 블록 시작 지점)
```

즉 이 두 파일을 지금 그대로 dbdiagram.io에 붙여넣으면 **다이어그램 자체가 안 열립니다.** `checks {}` 구문은 DBML 스펙에 아직 없는(또는 다른 툴/문서에서 착각한) 문법으로 보입니다.

**조치**: 모든 `checks {}` 블록을 제거하고, 내용은 그대로 `career_fit_final_constraints.sql` / `ai_service_final_constraints.sql`로 옮겼습니다. 수정한 두 `.dbml` 파일을 같은 `@dbml/core`로 다시 파싱 → PostgreSQL DDL export → 로컬 PostgreSQL 16에 실제로 `CREATE TABLE`부터 끝까지 실행해서 **에러 0건**인 것까지 확인했습니다. 이제 dbdiagram.io에 그대로 붙여넣으면 열립니다.

## 🟡 추가로 찾은 문제 (수정 완료)

### 1. `ai_tasks`: "작업 유형별 필수 대상 컬럼" 규칙이 note로만 있고 실제로 강제되지 않음

원본은 이 규칙을 테이블 note에 설명만 해뒀지 CHECK로 걸지 않았습니다. `external_job_posting_id` 없는 `MATCH` 작업이 애플리케이션 버그로 그냥 INSERT될 수 있는 상태였습니다. `ck_ai_task_targets` CHECK를 추가해 DB 레벨에서 막았습니다. (스모크 테스트로 실제 차단 확인)

### 2. `409 DRAFT_ALREADY_RUNNING`이 API 명세에는 있는데 DB에 이걸 보장할 장치가 없었음

`idempotency_key`는 `input_hash`(요청 내용) 기반이라, 같은 문항에 `experienceIds`를 다르게 넣어 재요청하면 키가 달라져서 동시에 여러 DRAFT가 돌 수 있는 구조였습니다. `(external_user_id, external_question_id)` 기준 부분 유니크 인덱스(`uq_ai_task_draft_inflight`, PENDING/RUNNING만 대상)를 추가했고, 같은 논리로 `EXPERIENCE_INTAKE`/`MATCH`/`POSTING_COMPETENCY_EXTRACTION`에도 동일하게 걸었습니다. 스모크 테스트로 "입력값이 달라도 같은 대상이면 두 번째는 막히고, 첫 작업이 끝나면 다시 허용된다"까지 확인했습니다.

### 3. `job_posting_questions.source_ai_task_id`가 가리키는 작업 유형이 정의돼 있지 않음

`ai_task_type` enum에는 `POSTING_COMPETENCY_EXTRACTION`(역량 추출)만 있고 "문항 추출"에 해당하는 타입이 없는데, `job_posting_questions.source_ai_task_id`의 원본 note는 "문항 추출 작업 ID"라고만 적혀 있었습니다. **같은 공고 원문을 한 번 분석해서 역량과 문항을 동시에 뽑아내는 하나의 작업**이라고 해석하고 note를 그렇게 명시했습니다. 만약 실제로는 별도 파이프라인(예: 크롤링 단계에서 정규식/파싱으로 문항만 먼저 뽑고, AI는 나중에 역량만 뽑는 구조)이라면 이 해석이 틀렸을 수 있으니 **팀 확인이 필요합니다.**

### 4. `GET /api/postings/{postingId}/match` — 매칭이 한 번도 트리거된 적 없는 경우가 명세에 없었음

`job_matches` 행도 `ai_tasks(MATCH)` 행도 전혀 없는 상태(예: 사용자가 아직 경험을 하나도 등록 안 함)의 응답이 정의돼 있지 않아서, 그대로 두면 프론트가 이 경우를 `RUNNING`으로 오인해 끝없이 폴링할 위험이 있었습니다. `status: "NOT_COMPUTED"`를 추가하고, `job_matches`/`ai_tasks` 중 어느 걸 언제 참조해야 하는지 API 가이드에 명시했습니다.

## 🟢 확인했지만 고치지 않고 팀 판단에 맡긴 것

### `answer_requirement_results` / `job_posting_questions.requirements` — 실제로 쓰이는지 불확실

`api-spec-final-change-guide.md`의 어느 API 응답에도 `requirements`나 `addressed`가 노출되지 않습니다. 스키마에는 남아있는데 API 계약에는 없는 상태라, 화면에서 실제로 쓰는 기능인지 확인이 필요합니다. 쓰지 않는다면 두 테이블 다 빼는 게 스키마를 더 최종본답게 만듭니다 — 다만 도메인 개념을 임의로 삭제하는 건 제 판단 범위를 넘는다고 봐서 이번엔 지우지 않고 note로만 표시해뒀습니다.

### `answer_experiences` RESTRICT + `experiences`에 소프트 삭제 컬럼 없음

경험 삭제 API가 현재 API 목록(17개)에 없으므로 당장 문제는 안 됩니다. 다만 나중에 "경험 삭제" 기능이 추가되면, 자소서 답변 근거로 쓰인 경험은 RESTRICT 때문에 그냥 DELETE가 안 됩니다(스모크 테스트로 확인). 그때 가서 `experiences.deleted_at` 같은 소프트 삭제 컬럼을 추가하면 됩니다 — 지금 미리 넣지는 않았습니다(안 쓰는 컬럼을 미리 넣는 것도 이번 "최종본 최소화" 방향과 안 맞다고 판단).

### Kafka/Outbox 없이 트랜잭션 커밋 후 비동기 실행기로 AI Service를 호출하는 방식의 리스크

MVP 범위 결정으로 존중하지만, 앱이 "경험 저장 커밋"과 "AI Service 호출" 사이에서 죽으면 매칭 재계산이 아무 기록 없이 누락됩니다. `job_matches.input_hash`가 최신 경험 상태와 달라도 이를 감지해서 재시도하는 장치가 현재 없습니다. 지금 당장 고치라는 건 아니고, 나중에 "매칭 점수가 이상하게 안 바뀐다"는 문의가 오면 여기부터 의심하시라고 남겨둡니다.

## 실제 검증 방법

1. `@dbml/core`(dbdiagram.io와 동일 파서, 10.1.1)로 두 `.dbml` 파일 파싱 → 원본은 실패, 수정본은 성공 → PostgreSQL DDL export.
2. 로컬 PostgreSQL 16에 `career_fit_final_test`, `ai_service_final_test` 두 DB 생성 → DDL + `*_constraints.sql` 순서대로 실행 → `ON_ERROR_STOP=1` 기준 에러 0건.
3. `smoke_test_career_fit_final.sql` (6개 케이스), `smoke_test_ai_service_final.sql` (6개 케이스) 전부 INSERT/UPDATE/DELETE로 실제 실행해 PASS 확인. 특히:
   - `cover_letter_answers` 사용자+문항당 1행 유니크 + UPDATE로 upsert되는 것
   - `answer_experiences` RESTRICT로 근거 경험 삭제가 실제로 막히는 것
   - `ai_tasks` 작업 유형별 필수 대상 CHECK
   - **같은 문항에 입력값만 다르게 재요청해도 동시 DRAFT가 DB 레벨에서 막히고, 이전 작업이 끝나면 다시 허용되는 것** (이번에 새로 추가한 보호장치)

두 스모크 테스트 파일을 그대로 재실행하면 동일한 결과를 재현할 수 있습니다.

## 최종 판단

**checks {} 파싱 오류만 고치면(이미 고쳤음) 이 ERD는 그대로 구현 가능합니다.** 나머지는 이미 잘 설계돼 있고, 제가 추가한 CHECK/부분 유니크 인덱스는 명세에 이미 약속된 동작(작업 대상 정합성, 409 동시실행 방지)을 DB 레벨에서 보장하는 보강일 뿐 설계를 바꾸는 게 아닙니다. 팀 확인이 필요한 건 위 "확인했지만 고치지 않은 것" 3개뿐입니다.
