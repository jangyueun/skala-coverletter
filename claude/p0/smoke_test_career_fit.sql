-- career_fit_test에서 실행: 핵심 제약이 실제로 동작하는지 확인

\echo '--- 1) users/competencies 기본 시드 ---'
INSERT INTO users (email, name) VALUES ('a@test.com','A'), ('b@test.com','B');
INSERT INTO competencies (name, category) VALUES ('Docker','TECH'), ('주도적 실행력','SOFT');

\echo '--- 2) competency_aliases UNIQUE(alias) : 같은 별칭 다른 역량 -> 실패해야 정상 ---'
INSERT INTO competency_aliases (competency_id, alias) VALUES (1, 'docker-alias');
DO $$
BEGIN
  BEGIN
    INSERT INTO competency_aliases (competency_id, alias) VALUES (2, 'docker-alias');
    RAISE EXCEPTION 'FAIL: 중복 alias가 들어감';
  EXCEPTION WHEN unique_violation THEN
    RAISE NOTICE 'PASS: 중복 alias 차단됨';
  END;
END $$;

\echo '--- 3) experience_competencies.strength 범위 CHECK ---'
INSERT INTO experiences (user_id, title, period_text) VALUES (1, 'exp1', '2026');
DO $$
BEGIN
  BEGIN
    INSERT INTO experience_competencies (experience_id, competency_id, strength) VALUES (1, 1, 1.50);
    RAISE EXCEPTION 'FAIL: strength 1.50이 들어감';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS: strength 범위 CHECK 동작함';
  END;
END $$;
INSERT INTO experience_competencies (experience_id, competency_id, strength) VALUES (1, 1, 0.80);

\echo '--- 4) companies/job_postings, applications 유니크(user_id, job_posting_id) ---'
INSERT INTO companies (name, normalized_name) VALUES ('세움테크', 'seumtech');
INSERT INTO job_postings (company_id, position, source) VALUES (1, '백엔드', 'CRAWLED');
INSERT INTO applications (user_id, job_posting_id, status) VALUES (1, 1, 'PLANNED');
DO $$
BEGIN
  BEGIN
    INSERT INTO applications (user_id, job_posting_id, status) VALUES (1, 1, 'WRITING');
    RAISE EXCEPTION 'FAIL: 같은 (user,posting) 중복 지원이 들어감';
  EXCEPTION WHEN unique_violation THEN
    RAISE NOTICE 'PASS: 중복 지원 차단됨';
  END;
END $$;

\echo '--- 5) applications submitted_at CHECK (PLANNED인데 submitted_at 채움 -> 실패) ---'
DO $$
BEGIN
  BEGIN
    INSERT INTO applications (user_id, job_posting_id, status, submitted_at)
      VALUES (2, 1, 'PLANNED', now());
    RAISE EXCEPTION 'FAIL: PLANNED + submitted_at 조합이 들어감';
  EXCEPTION WHEN unique_violation THEN
    RAISE NOTICE 'PASS(다른 이유로 차단, unique) — 케이스 조정 필요';
  WHEN check_violation THEN
    RAISE NOTICE 'PASS: submitted_at 정합성 CHECK 동작함';
  END;
END $$;

\echo '--- 6) cover_letter_answers 문항당 is_final 하나만 ---'
INSERT INTO cover_letter_questions (application_id, sequence, prompt_text)
  VALUES (1, 1, 'Q1');
INSERT INTO cover_letter_answers (question_id, version, content, is_final)
  VALUES (1, 1, 'v1', true);
DO $$
BEGIN
  BEGIN
    INSERT INTO cover_letter_answers (question_id, version, content, is_final)
      VALUES (1, 2, 'v2', true);
    RAISE EXCEPTION 'FAIL: is_final=true 두 번째 답변이 들어감';
  EXCEPTION WHEN unique_violation THEN
    RAISE NOTICE 'PASS: 문항당 is_final 하나 부분 유니크 인덱스 동작함';
  END;
END $$;

\echo '--- 모든 테스트 통과 시 여기까지 에러 없이 도달 ---'
