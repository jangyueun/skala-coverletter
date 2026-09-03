-- ============================================================
-- career_fit DB 추가 제약 (2026-09-03, v2)
-- dbdiagram.io의 DBML 문법은 컬럼 레벨 CHECK / 부분 유니크 인덱스를
-- 표현할 수 없으므로, career_fit.dbml에서 뽑은 DDL을 적용한 뒤
-- 이 스크립트를 반드시 실행한다.
-- ============================================================

-- user_auth_providers: LOCAL/GOOGLE에 따른 password_hash 정합성
ALTER TABLE user_auth_providers
  ADD CONSTRAINT chk_auth_password CHECK (
    (provider = 'LOCAL'  AND password_hash IS NOT NULL) OR
    (provider = 'GOOGLE' AND password_hash IS NULL)
  );

-- experience_competencies.strength 범위 (decimal(3,2)만으로는 9.99까지 저장 가능)
ALTER TABLE experience_competencies
  ADD CONSTRAINT chk_strength_range CHECK (strength >= 0 AND strength <= 1);

-- posting_competencies.weight 범위
ALTER TABLE posting_competencies
  ADD CONSTRAINT chk_weight_range CHECK (weight >= 0 AND weight <= 1);

-- job_matches.match_score 범위
ALTER TABLE job_matches
  ADD CONSTRAINT chk_match_score_range
  CHECK (match_score IS NULL OR (match_score >= 0 AND match_score <= 100));

-- cover_letter_questions
ALTER TABLE cover_letter_questions
  ADD CONSTRAINT chk_sequence_positive CHECK (sequence > 0);

ALTER TABLE cover_letter_questions
  ADD CONSTRAINT chk_length_limit_positive
  CHECK (length_limit IS NULL OR length_limit > 0);

-- cover_letter_answers
ALTER TABLE cover_letter_answers
  ADD CONSTRAINT chk_version_positive CHECK (version > 0);

ALTER TABLE cover_letter_answers
  ADD CONSTRAINT chk_char_count_nonneg CHECK (char_count IS NULL OR char_count >= 0);

ALTER TABLE cover_letter_answers
  ADD CONSTRAINT chk_byte_count_nonneg CHECK (byte_count IS NULL OR byte_count >= 0);

-- 문항당 최종(is_final=true) 답변은 하나만 허용
CREATE UNIQUE INDEX uq_answer_final
  ON cover_letter_answers (question_id)
  WHERE is_final = true;

-- competency_candidates 상태-컬럼 정합성
ALTER TABLE competency_candidates
  ADD CONSTRAINT chk_candidate_review CHECK (
    (status = 'PENDING'  AND reviewed_at IS NULL) OR
    (status = 'APPROVED' AND reviewed_at IS NOT NULL AND merged_competency_id IS NOT NULL) OR
    (status = 'REJECTED' AND reviewed_at IS NOT NULL)
  );

-- applications: 상태별 submitted_at 정합성
ALTER TABLE applications
  ADD CONSTRAINT chk_submitted_at CHECK (
    (status IN ('PLANNED', 'WRITING', 'CANCELLED') AND submitted_at IS NULL) OR
    (status IN ('SUBMITTED', 'DOCUMENT_PASSED', 'DOCUMENT_FAILED',
                'INTERVIEWING', 'FINAL_PASSED', 'FINAL_FAILED')
     AND submitted_at IS NOT NULL)
  );

-- competency_aliases: '%별칭%' 부분 문자열 검색용 GIN 인덱스
-- (alias 자체는 이미 UNIQUE btree라 정확 일치 조회는 그걸로 충분함)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
DROP INDEX IF EXISTS ix_alias_trgm;
CREATE INDEX ix_alias_trgm ON competency_aliases USING GIN (alias gin_trgm_ops);

-- updated_at 자동 갱신 트리거
-- (JPA Auditing(@LastModifiedDate)을 쓴다면 이 트리거는 생략 가능.
--  둘 다 켜두면 애플리케이션 값이 트리거로 다시 덮이므로 하나만 선택할 것.)
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
    'users', 'user_auth_providers', 'competencies', 'experiences',
    'experience_competencies', 'companies', 'job_postings',
    'posting_competencies', 'applications', 'cover_letter_questions'
  ]
  LOOP
    EXECUTE format(
      'CREATE TRIGGER trg_%1$s_updated_at BEFORE UPDATE ON %1$s
       FOR EACH ROW EXECUTE FUNCTION set_updated_at();', t
    );
  END LOOP;
END $$;
