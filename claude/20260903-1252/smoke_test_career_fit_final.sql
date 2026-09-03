-- career_fit_final_test에서 실행

\echo '--- 1) 시드 ---'
INSERT INTO users (supabase_user_id, email, name)
  VALUES ('11111111-1111-1111-1111-111111111111', 'a@test.com', 'A');
INSERT INTO competencies (name, category) VALUES ('클라우드 인프라', 'TECH');
INSERT INTO companies (name, normalized_name) VALUES ('한빛시스템', 'hanbitsystem');
INSERT INTO job_postings (company_id, position, source) VALUES (1, '플랫폼 엔지니어', 'CRAWLED');
INSERT INTO job_posting_questions (job_posting_id, sequence, prompt_text)
  VALUES (1, 1, '인프라 경험을 작성해 주세요.');

\echo '--- 2) experience_competencies strength 범위 CHECK ---'
INSERT INTO experiences (user_id, title, period_text) VALUES (1, 'EKS 배포', '2026');
DO $$
BEGIN
  BEGIN
    INSERT INTO experience_competencies (experience_id, competency_id, strength) VALUES (1, 1, 1.2);
    RAISE EXCEPTION 'FAIL: strength 1.2 통과됨';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS: strength 범위 CHECK 동작';
  END;
END $$;
INSERT INTO experience_competencies (experience_id, competency_id, strength) VALUES (1, 1, 0.9);

\echo '--- 3) job_posting_questions sequence/length_limit CHECK ---'
DO $$
BEGIN
  BEGIN
    INSERT INTO job_posting_questions (job_posting_id, sequence, prompt_text, length_limit)
      VALUES (1, 2, 'Q2', -100);
    RAISE EXCEPTION 'FAIL: length_limit 음수 통과됨';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS: length_limit CHECK 동작';
  END;
END $$;

\echo '--- 4) cover_letter_answers: 사용자+문항당 1행 (upsert 대상) ---'
INSERT INTO cover_letter_answers (user_id, job_posting_question_id, content, char_count, byte_count, source)
  VALUES (1, 1, '초안 내용', 5, 15, 'MANUAL');
DO $$
BEGIN
  BEGIN
    INSERT INTO cover_letter_answers (user_id, job_posting_question_id, content)
      VALUES (1, 1, '두 번째 INSERT는 막혀야 함');
    RAISE EXCEPTION 'FAIL: 같은 (user,question) 두 번째 답변 행이 들어감';
  EXCEPTION WHEN unique_violation THEN
    RAISE NOTICE 'PASS: 사용자+문항당 answer 1행 유니크 동작 (서비스 레이어는 UPDATE로 upsert)';
  END;
END $$;
UPDATE cover_letter_answers SET content = '수정된 내용', char_count = 6 WHERE user_id = 1 AND job_posting_question_id = 1;
\echo 'PASS: UPDATE로 upsert 동작 확인'

\echo '--- 5) job_matches match_score 범위 CHECK ---'
DO $$
BEGIN
  BEGIN
    INSERT INTO job_matches (user_id, job_posting_id, match_score, verdict, input_hash, computed_at)
      VALUES (1, 1, 150, 'RECOMMEND', 'h1', now());
    RAISE EXCEPTION 'FAIL: match_score 150 통과됨';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS: match_score 범위 CHECK 동작';
  END;
END $$;
INSERT INTO job_matches (user_id, job_posting_id, match_score, verdict, input_hash, computed_at)
  VALUES (1, 1, 85, 'RECOMMEND', 'h1', now());

\echo '--- 6) answer_experiences RESTRICT: 근거로 쓰인 경험은 삭제 불가 ---'
INSERT INTO answer_experiences (answer_id, experience_id) VALUES (1, 1);
DO $$
BEGIN
  BEGIN
    DELETE FROM experiences WHERE id = 1;
    RAISE EXCEPTION 'FAIL: 근거로 쓰인 경험이 삭제됨';
  EXCEPTION WHEN foreign_key_violation THEN
    RAISE NOTICE 'PASS: answer_experiences RESTRICT 동작 (경험 삭제 API가 있다면 소프트 삭제로 가야 함)';
  END;
END $$;

\echo '--- 모든 테스트 통과 시 여기까지 에러 없이 도달 ---'
