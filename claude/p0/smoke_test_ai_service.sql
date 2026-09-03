-- ai_service_test에서 실행

\echo '--- 1) CRAWL 작업: external_user_id NULL이어도 idempotency_key로 중복 방지 ---'
INSERT INTO ai_tasks (task_type, status, external_company_id, idempotency_key, input_hash)
  VALUES ('CRAWL', 'PENDING', 1, 'crawl:company:1:hash1', 'hash1');
DO $$
BEGIN
  BEGIN
    INSERT INTO ai_tasks (task_type, status, external_company_id, idempotency_key, input_hash)
      VALUES ('CRAWL', 'PENDING', 1, 'crawl:company:1:hash1', 'hash1');
    RAISE EXCEPTION 'FAIL: 동일 idempotency_key 중복 삽입이 들어감';
  EXCEPTION WHEN unique_violation THEN
    RAISE NOTICE 'PASS: user_id NULL인 CRAWL도 멱등성 보장됨';
  END;
END $$;

\echo '--- 2) MATCH 작업인데 external_job_posting_id 없음 -> CHECK로 차단되어야 함 ---'
DO $$
BEGIN
  BEGIN
    INSERT INTO ai_tasks (task_type, status, external_user_id, idempotency_key, input_hash)
      VALUES ('MATCH', 'PENDING', 1, 'match:missing-posting', 'hash2');
    RAISE EXCEPTION 'FAIL: job_posting_id 없는 MATCH 작업이 들어감';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS: 작업 유형별 필수 대상 컬럼 CHECK 동작함';
  END;
END $$;

\echo '--- 3) 정상 MATCH 작업 삽입 ---'
INSERT INTO ai_tasks (task_type, status, external_user_id, external_job_posting_id, idempotency_key, input_hash)
  VALUES ('MATCH', 'PENDING', 1, 10, 'match:1:10:hash3', 'hash3');
\echo 'PASS: 정상 MATCH 작업 삽입됨'

\echo '--- 4) retry_count 음수 차단 ---'
DO $$
BEGIN
  BEGIN
    INSERT INTO ai_tasks (task_type, status, external_company_id, idempotency_key, input_hash, retry_count)
      VALUES ('CRAWL', 'PENDING', 2, 'crawl:company:2:hash4', 'hash4', -1);
    RAISE EXCEPTION 'FAIL: retry_count 음수가 들어감';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS: retry_count 음수 CHECK 동작함';
  END;
END $$;

\echo '--- 모든 테스트 통과 시 여기까지 에러 없이 도달 ---'
