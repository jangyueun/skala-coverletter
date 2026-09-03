-- ai_service_final_test에서 실행

\echo '--- 1) 정상 MATCH 작업 ---'
INSERT INTO ai_tasks (task_type, status, external_user_id, external_job_posting_id, idempotency_key, input_hash, request_payload)
  VALUES ('MATCH', 'PENDING', 1, 10, 'match:1:10:h1', 'h1', '{}');
\echo 'PASS: 정상 MATCH 삽입'

\echo '--- 2) 작업 유형별 필수 대상 CHECK: MATCH인데 job_posting 없음 -> 차단 ---'
DO $$
BEGIN
  BEGIN
    INSERT INTO ai_tasks (task_type, status, external_user_id, idempotency_key, input_hash, request_payload)
      VALUES ('MATCH', 'PENDING', 1, 'match:missing', 'h2', '{}');
    RAISE EXCEPTION 'FAIL: job_posting_id 없는 MATCH 통과됨';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS: ck_ai_task_targets 동작';
  END;
END $$;

\echo '--- 3) 상태-시각 정합성 CHECK: PENDING인데 started_at 채움 -> 차단 ---'
DO $$
BEGIN
  BEGIN
    INSERT INTO ai_tasks (task_type, status, external_user_id, external_question_id, idempotency_key, input_hash, request_payload, started_at)
      VALUES ('DRAFT', 'PENDING', 1, 501, 'draft:pending-with-started', 'h3', '{}', now());
    RAISE EXCEPTION 'FAIL: PENDING + started_at 조합 통과됨';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS: ck_ai_task_timestamps 동작';
  END;
END $$;

\echo '--- 4) DRAFT 동시 실행 방지: 입력값(input_hash)이 달라도 같은 (user,question)이면 2번째는 차단 ---'
INSERT INTO ai_tasks (task_type, status, external_user_id, external_question_id, idempotency_key, input_hash, request_payload, started_at)
  VALUES ('DRAFT', 'RUNNING', 1, 501, 'draft:1:501:h4', 'h4', '{}', now());
DO $$
BEGIN
  BEGIN
    INSERT INTO ai_tasks (task_type, status, external_user_id, external_question_id, idempotency_key, input_hash, request_payload)
      VALUES ('DRAFT', 'PENDING', 1, 501, 'draft:1:501:h5-different-input', 'h5', '{}');
    RAISE EXCEPTION 'FAIL: idempotency_key만 다른 두 번째 동시 DRAFT가 통과됨 (API의 409 DRAFT_ALREADY_RUNNING 보장 실패)';
  EXCEPTION WHEN unique_violation THEN
    RAISE NOTICE 'PASS: uq_ai_task_draft_inflight 동작 — 같은 문항에 동시 DRAFT 1개만 허용';
  END;
END $$;
-- 같은 대상이라도 이전 작업이 끝나면(RUNNING->COMPLETED) 새 DRAFT는 허용돼야 함
UPDATE ai_tasks SET status = 'COMPLETED', started_at = now(), completed_at = now() WHERE idempotency_key = 'draft:1:501:h4';
INSERT INTO ai_tasks (task_type, status, external_user_id, external_question_id, idempotency_key, input_hash, request_payload)
  VALUES ('DRAFT', 'PENDING', 1, 501, 'draft:1:501:h5-different-input', 'h5', '{}');
\echo 'PASS: 이전 작업 종료 후 같은 문항 재요청은 정상 삽입됨'

\echo '--- 5) retry_count 음수 차단 ---'
DO $$
BEGIN
  BEGIN
    INSERT INTO ai_tasks (task_type, status, external_job_posting_id, idempotency_key, input_hash, request_payload, retry_count)
      VALUES ('POSTING_COMPETENCY_EXTRACTION', 'PENDING', 20, 'extract:20:h6', 'h6', '{}', -1);
    RAISE EXCEPTION 'FAIL: retry_count 음수 통과됨';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS: ck_ai_task_retry_count 동작';
  END;
END $$;

\echo '--- 6) ai_task_attempts attempt_no > 0 CHECK ---'
INSERT INTO ai_tasks (task_type, status, external_job_posting_id, idempotency_key, input_hash, request_payload, started_at, completed_at)
  VALUES ('POSTING_COMPETENCY_EXTRACTION', 'FAILED', 30, 'extract:30:h7', 'h7', '{}', now(), now());
DO $$
DECLARE tid bigint;
BEGIN
  SELECT id INTO tid FROM ai_tasks WHERE idempotency_key = 'extract:30:h7';
  BEGIN
    INSERT INTO ai_task_attempts (ai_task_id, attempt_no, status, started_at)
      VALUES (tid, 0, 'FAILED', now());
    RAISE EXCEPTION 'FAIL: attempt_no 0 통과됨';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS: ck_ai_task_attempt_no 동작';
  END;
END $$;

\echo '--- 모든 테스트 통과 시 여기까지 에러 없이 도달 ---'
