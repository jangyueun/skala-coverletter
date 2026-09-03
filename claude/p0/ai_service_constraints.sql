-- ============================================================
-- ai_service DB 추가 제약 (2026-09-03, v2)
-- ai_service.dbml에서 뽑은 DDL을 적용한 뒤 실행한다.
-- ============================================================

ALTER TABLE ai_tasks
  ADD CONSTRAINT chk_retry_count_nonneg CHECK (retry_count >= 0);

-- task_type별 필수 external_* 컬럼 정합성
ALTER TABLE ai_tasks
  ADD CONSTRAINT chk_task_targets CHECK (
    (task_type = 'MATCH'   AND external_user_id IS NOT NULL AND external_job_posting_id IS NOT NULL) OR
    (task_type = 'DRAFT'   AND external_user_id IS NOT NULL AND external_question_id IS NOT NULL) OR
    (task_type = 'EXTRACT' AND external_job_posting_id IS NOT NULL) OR
    (task_type = 'CRAWL'   AND external_company_id IS NOT NULL) OR
    (task_type = 'INTAKE'  AND external_user_id IS NOT NULL)
  );

ALTER TABLE ai_task_attempts
  ADD CONSTRAINT chk_attempt_no_positive CHECK (attempt_no > 0);
