-- ============================================================
-- career_fit_final_slack DB 추가 제약 (2026-09-03)
-- 참고용 제약 모음. 실제 적용본은 backend의 Flyway 마이그레이션에서 버전 관리한다.
-- 이 파일을 Flyway와 별도로 중복 실행하지 않는다.
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

-- 비활성화한 이전 문항은 보존하고, 활성 문항끼리만 공고/순번 중복을 금지한다.
CREATE UNIQUE INDEX uq_job_posting_question_active_sequence
  ON job_posting_questions (job_posting_id, sequence)
  WHERE is_active = true;

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
    'competencies', 'experiences', 'experience_competencies',
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
