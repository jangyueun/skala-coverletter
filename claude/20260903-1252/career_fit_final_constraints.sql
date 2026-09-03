-- ============================================================
-- career_fit_final DB 추가 제약 (2026-09-03, 리뷰 반영)
-- career_fit_final.dbml에서 뽑은 DDL을 적용한 뒤 실행한다.
--
-- 원본 초안(career_fit_final.dbml v0)은 CHECK를 DBML의 `checks {}` 블록으로
-- 직접 넣었으나, dbdiagram.io가 실제로 쓰는 @dbml/core(최신 10.1.1)가
-- 이 문법을 파싱하지 못해(SyntaxError) 다이어그램 자체를 열 수 없는 상태였다.
-- 그래서 모든 CHECK를 여기로 옮기고 dbdiagram.io에 그대로 붙여넣어
-- 파싱되는 것까지 확인했다.
-- ============================================================

ALTER TABLE experience_competencies
  ADD CONSTRAINT ck_experience_competency_strength CHECK (strength >= 0 AND strength <= 1);

ALTER TABLE posting_competencies
  ADD CONSTRAINT ck_posting_competency_weight CHECK (weight >= 0 AND weight <= 1);

ALTER TABLE posting_competencies
  ADD CONSTRAINT ck_posting_evidence_offsets CHECK (
    (evidence_start_offset IS NULL AND evidence_end_offset IS NULL) OR
    (evidence_start_offset >= 0 AND evidence_end_offset >= evidence_start_offset)
  );

ALTER TABLE job_posting_questions
  ADD CONSTRAINT ck_posting_question_sequence CHECK (sequence > 0);

ALTER TABLE job_posting_questions
  ADD CONSTRAINT ck_posting_question_length_limit CHECK (length_limit IS NULL OR length_limit > 0);

ALTER TABLE job_matches
  ADD CONSTRAINT ck_job_match_score CHECK (match_score >= 0 AND match_score <= 100);

ALTER TABLE cover_letter_answers
  ADD CONSTRAINT ck_answer_char_count CHECK (char_count >= 0);

ALTER TABLE cover_letter_answers
  ADD CONSTRAINT ck_answer_byte_count CHECK (byte_count >= 0);

ALTER TABLE answer_requirement_results
  ADD CONSTRAINT ck_answer_requirement_seq CHECK (requirement_seq > 0);

-- competency_aliases: '%별칭%' 부분 문자열 검색용 GIN 인덱스
-- (alias 자체는 이미 UNIQUE btree라 정확 일치 조회는 그걸로 충분)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_alias_trgm ON competency_aliases USING GIN (alias gin_trgm_ops);

-- updated_at 자동 갱신 트리거
-- (JPA Auditing(@LastModifiedDate)을 쓴다면 이 트리거는 생략할 것.
--  둘 다 켜두면 애플리케이션 값이 트리거로 다시 덮이므로 하나만 선택.)
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'users', 'competencies', 'experiences', 'experience_competencies',
    'companies', 'job_postings', 'posting_competencies',
    'job_posting_questions', 'job_matches', 'cover_letter_answers',
    'answer_requirement_results'
  ]
  LOOP
    EXECUTE format(
      'CREATE TRIGGER trg_%1$s_updated_at BEFORE UPDATE ON %1$s
       FOR EACH ROW EXECUTE FUNCTION set_updated_at();', t
    );
  END LOOP;
END $$;
