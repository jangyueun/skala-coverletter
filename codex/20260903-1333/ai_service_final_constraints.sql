-- ============================================================
-- ai_service_final DB 추가 제약 (2026-09-03, 리뷰 반영)
-- ai_service_final.dbml에서 뽑은 DDL을 적용한 뒤 실행한다.
--
-- DBML은 다이어그램 문서로 사용하고, DB 고유 CHECK/부분 인덱스는 여기서 관리한다.
-- ============================================================

ALTER TABLE ai_tasks
  ADD CONSTRAINT ck_ai_task_retry_count CHECK (retry_count >= 0);

ALTER TABLE ai_tasks
  ADD CONSTRAINT ck_ai_task_total_tokens CHECK (total_tokens IS NULL OR total_tokens >= 0);

ALTER TABLE ai_tasks
  ADD CONSTRAINT ck_ai_task_latency CHECK (latency_ms IS NULL OR latency_ms >= 0);

ALTER TABLE ai_tasks
  ADD CONSTRAINT ck_ai_task_timestamps CHECK (
    (status = 'PENDING' AND started_at IS NULL AND completed_at IS NULL) OR
    (status = 'RUNNING' AND started_at IS NOT NULL AND completed_at IS NULL) OR
    (status IN ('COMPLETED', 'FAILED') AND started_at IS NOT NULL AND completed_at IS NOT NULL)
  );

-- [리뷰 추가] 원본에 note로만 설명돼 있던 "작업 유형별 필수 대상 컬럼" 규칙을
-- 실제 CHECK로 강제. 이게 없으면 예를 들어 external_job_posting_id 없는 MATCH
-- 작업이 애플리케이션 버그로 그냥 들어가버릴 수 있음.
ALTER TABLE ai_tasks
  ADD CONSTRAINT ck_ai_task_targets CHECK (
    (task_type = 'POSTING_COMPETENCY_EXTRACTION' AND external_job_posting_id IS NOT NULL) OR
    (task_type = 'EXPERIENCE_INTAKE'             AND external_user_id IS NOT NULL) OR
    (task_type = 'MATCH'                          AND external_user_id IS NOT NULL AND external_job_posting_id IS NOT NULL) OR
    (task_type = 'DRAFT'                          AND external_user_id IS NOT NULL AND external_question_id IS NOT NULL)
  );

ALTER TABLE ai_task_attempts
  ADD CONSTRAINT ck_ai_task_attempt_no CHECK (attempt_no > 0);

-- [리뷰 추가] API 명세(api-spec-final-change-guide.md §10)는
-- "같은 문항에 대해 이미 DRAFT가 실행 중이면 409 DRAFT_ALREADY_RUNNING"을 약속하는데,
-- idempotency_key는 입력값(input_hash 등) 기반이라 experienceIds를 바꿔서 다시 요청하면
-- 그냥 통과해 동시에 여러 DRAFT가 돌 수 있었다. 대상 기준 부분 유니크 인덱스로 원천 차단.
CREATE UNIQUE INDEX uq_ai_task_draft_inflight
  ON ai_tasks (external_user_id, external_question_id)
  WHERE task_type = 'DRAFT' AND status IN ('PENDING', 'RUNNING');

CREATE UNIQUE INDEX uq_ai_task_intake_inflight
  ON ai_tasks (external_user_id)
  WHERE task_type = 'EXPERIENCE_INTAKE' AND status IN ('PENDING', 'RUNNING');

CREATE UNIQUE INDEX uq_ai_task_extraction_inflight
  ON ai_tasks (external_job_posting_id)
  WHERE task_type = 'POSTING_COMPETENCY_EXTRACTION' AND status IN ('PENDING', 'RUNNING');

-- MATCH는 실행 중 경험이 다시 바뀌면 최신 요청이 누락될 수 있어 단일 실행 인덱스를 두지 않는다.
-- 결과 반영 직전에 task.input_hash와 현재 경험 입력 해시를 비교하고, 다르면 결과를 폐기한 뒤 재요청한다.
