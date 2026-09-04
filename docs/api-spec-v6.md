# Career Lab API 명세 v6 (2026-09-03)

v5 → v6 (회의 결정 반영)
- 공고에서 직무 계열 `role` 삭제. 홈의 직무 계열 필터는 없어지고, 상세의 "비슷한 직무"는 요구 역량 태그 겹침으로 계산한다. 수집 출처·문항 상태·미등록 용어도 삭제.
- `deadline`은 시각 포함 ISO 문자열(`2026-09-12T18:00:00+09:00`). D-day·마감 판정은 시각 기준.
- 문항은 관리자 일괄 관리. 사용자 문항 등록 API 삭제. 문항 응답에서 `source`·`lengthUnit` 삭제.
- 경험은 `startDate`·`endDate`(둘 다 NULL 허용, 월 입력은 1일). `source`·`intake` 필드 삭제. 인테이크 등록 여부는 `aiTaskId` 유무.
- 인테이크는 링크 + 첨부파일(PDF·MD·TXT)을 multipart로 받는다. 파일은 Supabase Storage에 저장하고 AI 서버에는 URL만 전달.
- 답변 `source` 삭제. 매칭 `computedAt` → `updatedAt`.
- AI 계약 응답에 `promptVersion`·`model` 추가, 프롬프트 버전 조회 계약 추가.
- 같은 입력의 AI 작업 재요청은 200 + 기존 `taskId`.

전제
- 인증: Slack OAuth + 서버 세션 쿠키. 모든 `/api/**`는 세션 필수(401 `LOGIN_REQUIRED`). 프론트는 `credentials: 'include'`.
- AI 4종은 실제 LLM 전제의 비동기 작업. `202` + `taskId` 후 `GET /api/ai-tasks/{taskId}` 폴링. 현재 구현은 `MockAiClient`.
- 성공 응답은 DTO 그대로. 오류는 `{"code": "...", "message": "..."}`.
- ERD `docs/erd-v6.dbml`(dbdiagram.io에 붙여넣기). CHECK·부분 UNIQUE·인덱스·트리거는 이 문서 부록.

## 조회 규칙 (N+1 방지)

| 화면 | 쿼리 수 | 방법 |
|---|---|---|
| 공고 목록 | 6 | 매칭순 상위 N 1 · 공고+기업 프로젝션(content 제외) 1 · `job_matches` IN 1 · `bookmarks` IN 1 · 문항 수 GROUP BY 1 · 답변 수 GROUP BY 1 |
| 공고 상세 | 4 | 공고+기업 · 요구 역량 · 매칭 결과 · 관련 공고(같은 기업 IN + 태그 겹침 상위 3) |
| 문항+답변 | 1 | 문항 LEFT JOIN 답변. 근거 경험 ID는 배열 컬럼 |
| 경험 목록 | 3 | 경험 · 역량 IN · 사용 건수(사용자 답변 `unnest` GROUP BY) |
| 매칭 탭 | 2 | `job_matches` 1행 · coverage 안의 경험 ID IN |
| 역량 사전 | 0 | 앱 시작 시 역량+별칭 메모리 캐시 |

## 공통 상태 코드

| 코드 | 의미 |
|---|---|
| 200 | 조회·저장 성공, 같은 입력의 AI 작업 재요청(기존 taskId) |
| 201 | 경험 등록 |
| 202 | AI 작업 접수 `{"taskId": 812}` |
| 204 | 로그아웃 |
| 302 | Slack 인가 페이지·프론트로 리다이렉트 |
| 400 `VALIDATION_FAILED` | 검증 실패 |
| 401 `LOGIN_REQUIRED` | 미로그인 |
| 403 `FORBIDDEN` / `WORKSPACE_NOT_ALLOWED` | 남의 자원 / 허용되지 않은 워크스페이스 |
| 404 `*_NOT_FOUND` | 대상 없음 |
| 409 `*_ALREADY_RUNNING` | 같은 대상에 다른 입력으로 진행 중인 AI 작업 |
| 413 `FILE_TOO_LARGE` | 인테이크 첨부 용량 초과 |

## 1. 인증 (구현 완료 4개)

| API | Request | Response | 상태 |
|---|---|---|---|
| `GET /api/auth/slack/start` | 없음 | Slack 인가 URL로 이동 | 302 |
| `GET /api/auth/slack/callback` | Query `code`, `state` | 세션 생성 후 프론트 `/my`로 이동 | 302 · 400 `STATE_MISMATCH` · 403 `WORKSPACE_NOT_ALLOWED` |
| `GET /api/auth/me` | 없음 | `{"id":1,"displayName":"김지호","email":"a@b.c","avatarUrl":"..."}` 또는 미로그인 `null` | 200 |
| `POST /api/auth/logout` | 없음 | 없음 | 204 |

## 2. 역량 사전 (1개)

| API | Request | Response | 상태 |
|---|---|---|---|
| `GET /api/competencies` | Query `category?` | `[{"id":1,"name":"장애 대응·모니터링","category":"ROLE","aliases":["트러블슈팅","RCA"]}]` · 범주 라벨(직무 역량/기술·언어/일하는 방식/산업/인재상)과 약칭(직무/기술/협업/산업/인재상)은 프론트 상수 | 200 |

## 3. 공고 (5개)

| API | Request | Response | 상태 |
|---|---|---|---|
| `GET /api/postings` | Query `q?` 기업·직무·역량명, `competencyId?` 반복(OR), `bookmarked?`, `sort=match\|deadline`, `includeClosed?=false`, `page?=0`, `size?=20` | `{"items":[{"id":9,"company":"세움테크","position":"백엔드 엔지니어 (신입)","deadline":"2026-09-12T18:00:00+09:00","status":"ACTIVE","bookmarked":false,"match":{"score":75,"verdict":"CONDITIONAL","coveredCompetencyNames":["Spring Boot","Java·Kotlin","API 설계·연동"],"requiredCount":14},"essay":{"state":"WRITING","answered":1,"total":4}}],"page":0,"size":20,"totalCount":57}` · `content`·`role` 없음 · `match` 미계산이면 `null` · `essay.state` NO_QUESTIONS·EMPTY·WRITING·DONE | 200 · 401 |
| `GET /api/postings/{postingId}` | Path | `{"id":9,"company":"세움테크","position":"...","deadline":"2026-09-12T18:00:00+09:00","status":"ACTIVE","sourceUrl":"...","content":"[세움테크] ...","bookmarked":false,"requiredCompetencies":[{"competencyId":3,"name":"API 설계·연동","category":"ROLE","weight":0.9,"evidenceLine":"REST API 설계 및 운영 경험"}],"related":{"sameCompany":[{"id":14,"position":"프론트엔드 엔지니어 (신입)","score":51}],"similar":[{"id":10,"company":"다온소프트","position":"서버 개발 (신입)","sharedCompetencyCount":9,"score":63}]}}` · `similar`는 다른 기업 중 요구 역량 겹침 수 상위 3 | 200 · 401 · 404 `POSTING_NOT_FOUND` |
| `GET /api/postings/{postingId}/questions` | Path · 사용자는 세션에서 | `[{"id":31,"sequence":1,"promptText":"...","lengthLimit":700,"answer":{"content":"...","charCount":210,"usedExperienceIds":[1],"updatedAt":"..."}}]` · `answer`는 로그인 사용자 것만, 없으면 `null` | 200 · 401 · 404 |
| `GET /api/postings/{postingId}/match` | Path | `{"status":"COMPLETED","taskId":812,"score":75,"verdict":"CONDITIONAL","coveredCount":8,"requiredCount":14,"updatedAt":"...","rows":[{"competencyId":3,"name":"API 설계·연동","category":"ROLE","weight":0.9,"score":1.0,"isGap":false,"evidenceLine":"...","experiences":[{"id":1,"title":"MSA 주문·결제 서비스 구축","result":"...","strength":0.8}]}]}` · `status` NOT_COMPUTED·PENDING·RUNNING·COMPLETED·FAILED. 결과가 없거나 stale이면 서버가 MATCH 작업을 만들고 PENDING | 200 · 401 · 404 |
| `PUT /api/postings/{postingId}/bookmark` | Body `{"bookmarked":true}` | `{"postingId":9,"bookmarked":true}` | 200 · 400 · 401 · 404 |

## 4. 경험 (4개)

| API | Request | Response | 상태 |
|---|---|---|---|
| `GET /api/experiences` | Query `competencyId?` | `[{"id":1,"title":"MSA 주문·결제 서비스 구축","category":"TEAM_PROJECT","startDate":"2026-08-01","endDate":null,"situation":"...","task":"...","action":"...","result":"...","aiTaskId":null,"competencies":[{"competencyId":4,"name":"대용량 트래픽·분산 처리","strength":0.8}],"usedInQuestions":2}]` · 기간 표시 문자열("2026.08", "2025.03 – 2025.11")은 프론트가 만든다 | 200 · 401 |
| `POST /api/experiences` | Body `{"title":"...","category":"TEAM_PROJECT","startDate":"2026-08-01","endDate":null,"situation":"...","task":"...","action":"...","result":"...","competencies":[{"competencyId":4,"strength":0.8}],"intakeTaskId":790}` · `intakeTaskId`는 인테이크 후보에서 등록할 때만 | `{"experience":{...},"reassess":{"postingCount":7,"taskIds":[813,814,815,816,817,818,819]}}` · 저장 후 활성 공고마다 MATCH 작업 | 201 · 400 `VALIDATION_FAILED`(제목·결과·역량 1개 이상·endDate ≥ startDate) · 401 |
| `PUT /api/experiences/{experienceId}` | POST와 동일(`intakeTaskId` 제외) | POST와 동일 | 200 · 400 · 401 · 403 · 404 `EXPERIENCE_NOT_FOUND` |
| `POST /api/experience-intakes` | **multipart/form-data**: `links` 텍스트(줄바꿈 구분) · `files[]` PDF·MD·TXT, 파일당 10 MB, 최대 5개 | `{"taskId":790}` · 서버가 파일을 Supabase Storage `intake/{userId}/{taskId}/`에 올리고 URL을 작업 입력에 기록. 결과는 `GET /api/ai-tasks/790`의 `result.candidates` | 202 · 200(같은 입력 진행 중) · 400(링크·파일 모두 없음, 허용되지 않은 형식) · 401 · 409 `INTAKE_ALREADY_RUNNING` · 413 `FILE_TOO_LARGE` |

## 5. 자기소개서 (3개)

| API | Request | Response | 상태 |
|---|---|---|---|
| `GET /api/questions/{questionId}/answer` | Path | `{"questionId":31,"content":"...","charCount":210,"usedExperienceIds":[1],"aiTaskId":null,"updatedAt":"..."}` · 없으면 `content ""`, `usedExperienceIds []`, `updatedAt null` | 200 · 401 · 404 `QUESTION_NOT_FOUND` |
| `PUT /api/questions/{questionId}/answer` | Body `{"content":"...","usedExperienceIds":[1,4],"draftTaskId":821}` · `usedExperienceIds`는 본인 경험만(서비스 검사) | `{"questionId":31,"content":"...","charCount":612,"usedExperienceIds":[1,4],"aiTaskId":821,"updatedAt":"2026-09-03T15:10:42+09:00"}` | 200 · 400 · 401 · 403 `FORBIDDEN` · 404 |
| `POST /api/questions/{questionId}/drafts` | Body `{"experienceIds":[1,4]}` | `{"taskId":821}` · 결과는 `GET /api/ai-tasks/821`의 `result.draft`. 초안은 저장되지 않는다 | 202 · 200(같은 입력 진행 중) · 400 · 401 · 403 · 404 · 409 `DRAFT_ALREADY_RUNNING` |

## 6. AI 작업 (2개)

| API | Request | Response | 상태 |
|---|---|---|---|
| `GET /api/ai-tasks/{taskId}` | Path | `{"taskId":821,"type":"DRAFT","status":"COMPLETED","createdAt":"...","completedAt":"...","attempts":1,"model":"claude-opus-5","promptVersion":"draft/v1","result":{"draft":"...","charCount":698},"error":null}` · FAILED면 `"error":{"code":"AI_PROVIDER_ERROR","message":"..."}` · type별 result: DRAFT `{draft, charCount}` · EXPERIENCE_INTAKE `{candidates[]}` · MATCH `{postingId, score, verdict}` · POSTING_ANALYSIS `{postingId, requiredCount}` | 200 · 401 · 403 · 404 `TASK_NOT_FOUND` |
| `GET /api/ai-tasks` | Query `type?`, `status?` 반복, `since?` | `{"counts":{"pending":2,"running":1,"completed":4,"failed":0},"items":[{"taskId":813,"type":"MATCH","status":"RUNNING","postingId":9,"createdAt":"..."}]}` | 200 · 401 |

구현 메모 (2026-09-04) — 워커(`AiTaskWorker`, 5초 주기)가 타입별 처리기로 완료한다: DRAFT `DraftTaskHandler` · EXPERIENCE_INTAKE
`ExperienceIntakeTaskHandler` · MATCH `MatchTaskHandler`(요구 역량이 없는 공고는 `job_matches` 를 쓰지 않고 result 의 `score`·`verdict` 가 `null`) ·
POSTING_ANALYSIS `PostingAnalysisTaskHandler`. 멱등 키에는 AI 서버의 promptVersion 이 들어간다(`PromptVersionRegistry`). 목록의 `attempts`
는 재시도 수 + 1 이다.

## 7. 내부 (1개)

| API | Request | Response | 상태 |
|---|---|---|---|
| `POST /internal/postings/{postingId}/analysis` | Header `X-Internal-Token` | `{"taskId":701}` · 완료 시 `posting_competencies` 교체, `analyzed_at` 갱신 | 202 · 200(진행 중이면 기존 taskId) · 401 `INTERNAL_TOKEN_INVALID` · 404 |

공개 API 19개 + 내부 1개 = 20개.

## 8. AI 제공자 계약 (Spring → Python AI 서버, 현재는 Mock)

AI 서버는 상태를 갖지 않는다. 요청을 받아 LLM을 호출하고 결과를 돌려줄 뿐이며, 작업 상태·재시도·결과 저장은 Spring의 `ai_tasks`가 맡는다. 모든 응답에 `promptVersion`·`model`을 싣는다. 실패는 `attempts`에 기록하고 최대 3회 지수 백오프 재시도, 소진 시 FAILED.

구현 (2026-09-04) — `ai/.env` 의 `ANTHROPIC_API_KEY` 가 있으면 `ClaudeAiProvider`, 없으면 `MockAiProvider`. 공고 분석·인테이크는 Sonnet 5, 초안은 Opus 5(구조화 출력). 인테이크는 `web_fetch` 로 링크·파일 URL 을 직접 읽고, 판단이 갈릴 때만 advisor(Opus 5, 기본 2회)에게 묻는다. **매칭은 LLM 없이 결정론 공식**이다(프론트 카드와 같은 식, `ai/app/services/matching.py`). 프롬프트와 버전은 `ai/app/services/prompts.py` — 문장을 고치면 버전을 올린다. 시스템 프롬프트·역량 사전은 프롬프트 캐시. 한 호출 상한은 AI 서버 300초 · Spring 330초.

| 계약 | Request | Response | 상태 |
|---|---|---|---|
| `GET /ai/prompts/versions` | 없음 | `{"posting_analysis":"v2","experience_intake":"v1","match":"v1","draft":"v1"}` · Spring이 시작 시 읽어 멱등 키 계산에 사용 | 200 |
| `POST /ai/posting-analysis` | `{"postingId":9,"content":"...","competencies":[{"id":3,"name":"API 설계·연동","category":"ROLE","aliases":["REST API 개발"]}]}` | `{"required":[{"competencyId":3,"weight":0.9,"evidence":"REST API 설계 및 운영 경험"}],"promptVersion":"posting_analysis/v2","model":"claude-opus-5"}` · 사전 밖 ID는 Spring이 버린다 | 200 · 422 · 503 |
| `POST /ai/experience-intake` | `{"links":["https://github.com/..."],"fileUrls":["https://.../intake/7/790/portfolio.pdf"],"existingExperiences":[{"id":1,"title":"...","startDate":"2026-08-01","endDate":null,"category":"TEAM_PROJECT"}],"competencies":[...]}` | `{"candidates":[{"key":"oss","title":"...","startDate":"2026-06-01","endDate":null,"category":"PERSONAL_PROJECT","situation":"...","action":"...","questions":[{"field":"task","q":"...","why":"..."}],"suggestedCompetencyIds":[14,6],"duplicateOfExperienceId":null}],"promptVersion":"experience_intake/v1","model":"..."}` | 200 · 422 · 503 |
| `POST /ai/match` | `{"posting":{"id":9,"required":[{"competencyId":3,"weight":0.9,"evidenceLine":"..."}]},"experiences":[{"id":1,"title":"...","result":"...","competencies":[{"competencyId":3,"strength":0.8}]}]}` | `{"overall":0.75,"verdict":"CONDITIONAL","rows":[{"competencyId":3,"weight":0.9,"score":1.0,"isGap":false,"experienceIds":[1,2]}],"promptVersion":"match/v1","model":"..."}` · `overall`을 `match_score`에 저장 | 200 · 422 · 503 |
| `POST /ai/draft` | `{"question":{"promptText":"...","lengthLimit":700},"posting":{"company":"세움테크","position":"...","content":"[세움테크] 2026 하반기 ...","required":[{"name":"API 설계·연동","weight":0.9,"evidenceLine":"REST API 설계 및 운영 경험"}]},"experiences":[{"title":"...","situation":"...","task":"...","action":"...","result":"..."}]}` · `content` 는 공고 원문 전문(없으면 `""`), `required` 는 요구 역량 이름·가중치·근거 문장 — 초안이 공고의 담당 업무·인재상 문장에 경험을 맞대게 하려고 넣는다(2026-09-04, `requiredNames` 를 대체) | `{"draft":"...","charCount":698,"promptVersion":"draft/v2","model":"..."}` | 200 · 422 · 503 |

## 9. 오류 코드

| code | 상태 | 언제 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | `@Valid` 실패, 허용되지 않은 첨부 형식 |
| `STATE_MISMATCH` | 400 | Slack 콜백 state 불일치 |
| `LOGIN_REQUIRED` | 401 | 세션 없음. `/api/auth/me`만 200 + null. `/api/**` 인터셉터가 컨트롤러 앞에서 준다 |
| `LOGIN_FAILED` | 401 | Slack 토큰 교환·프로필 조회 실패, 콜백에 code 없음 |
| `INTERNAL_TOKEN_INVALID` | 401 | `/internal/*` 토큰 불일치 |
| `WORKSPACE_NOT_ALLOWED` | 403 | 허용 워크스페이스 아님 |
| `FORBIDDEN` | 403 | 다른 사용자의 경험·답변·작업 |
| `CSRF_REJECTED` | 403 | 다른 사이트에서 온 상태 변경 요청(POST·PUT·PATCH·DELETE, `Sec-Fetch-Site: cross-site`). 같은 오리진의 프론트에서는 나올 수 없다 |
| `POSTING_NOT_FOUND` `QUESTION_NOT_FOUND` `EXPERIENCE_NOT_FOUND` `TASK_NOT_FOUND` | 404 | |
| `NOT_FOUND` | 404 | 없는 경로 (예전엔 500 `INTERNAL_ERROR` 로 샜다) |
| `METHOD_NOT_ALLOWED` | 405 | 있는 경로에 틀린 메서드 |
| `DRAFT_ALREADY_RUNNING` `INTAKE_ALREADY_RUNNING` `ANALYSIS_ALREADY_RUNNING` | 409 | 같은 대상에 다른 입력으로 진행 중 |
| `FILE_TOO_LARGE` | 413 | 인테이크 첨부 10 MB 초과 |
| `AI_PROVIDER_ERROR` | 작업 FAILED | AI 서비스 4xx·5xx·타임아웃. 재시도 소진 후 폴링 응답의 `error` |

## 10. 프론트 담당자에게

- 홈의 직무 계열 필터는 데이터가 없어진다. 제거하거나 역량 필터로 대체한다.
- 상세의 "다른 기업 · 비슷한 직무"는 `related.similar`(태그 겹침)로 그린다. 계열 라벨은 없다.
- 경험 등록의 기간 입력은 시작·종료 월 두 칸으로 바뀐다. 표시 문자열은 프론트가 만든다.
- 마감은 ISO 일시로 온다. D-day와 "마감됨"은 시각까지 비교한다.
- 인테이크는 multipart 전송이다.

## 부록. DBML 밖의 제약과 인덱스 (Flyway V2 끝부분)

```sql
-- Career Lab v6 — DBML로 표현하지 않는 제약과 인덱스. Flyway V2 마지막 부분에 포함한다.
-- (DBML의 enum 타입은 문서용. 실제 V2에서는 varchar + CHECK로 만들어 JPA EnumType.STRING과 맞춘다.)

-- ── 범위 CHECK ──────────────────────────────────────────────
ALTER TABLE experience_competencies
  ADD CONSTRAINT ck_experience_competency_strength CHECK (strength >= 0 AND strength <= 1);

ALTER TABLE experiences
  ADD CONSTRAINT ck_experience_period CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date);

ALTER TABLE posting_competencies
  ADD CONSTRAINT ck_posting_competency_weight CHECK (weight >= 0 AND weight <= 1);

ALTER TABLE job_posting_questions
  ADD CONSTRAINT ck_posting_question_sequence CHECK (sequence > 0),
  ADD CONSTRAINT ck_posting_question_length_limit CHECK (length_limit IS NULL OR length_limit > 0);

ALTER TABLE job_matches
  ADD CONSTRAINT ck_job_match_score CHECK (match_score >= 0 AND match_score <= 1),
  ADD CONSTRAINT ck_job_match_covered CHECK (covered_count >= 0);

ALTER TABLE cover_letter_answers
  ADD CONSTRAINT ck_answer_char_count CHECK (char_count >= 0);

-- ── AI 작업 대상·시각 정합성 ─────────────────────────────────
ALTER TABLE ai_tasks
  ADD CONSTRAINT ck_ai_task_targets CHECK (
    (task_type = 'POSTING_ANALYSIS'  AND job_posting_id IS NOT NULL) OR
    (task_type = 'EXPERIENCE_INTAKE' AND user_id IS NOT NULL) OR
    (task_type = 'MATCH'             AND user_id IS NOT NULL AND job_posting_id IS NOT NULL) OR
    (task_type = 'DRAFT'             AND user_id IS NOT NULL AND question_id IS NOT NULL)
  ),
  ADD CONSTRAINT ck_ai_task_retry CHECK (retry_count >= 0),
  ADD CONSTRAINT ck_ai_task_timestamps CHECK (
    (status = 'PENDING'   AND started_at IS NULL     AND completed_at IS NULL) OR
    (status = 'RUNNING'   AND started_at IS NOT NULL AND completed_at IS NULL) OR
    (status IN ('COMPLETED', 'FAILED') AND started_at IS NOT NULL AND completed_at IS NOT NULL)
  );

-- ── 부분 UNIQUE (같은 대상에 진행 중 작업 1개) ───────────────
CREATE UNIQUE INDEX uq_ai_task_draft_inflight
  ON ai_tasks (user_id, question_id)
  WHERE task_type = 'DRAFT' AND status IN ('PENDING', 'RUNNING');

CREATE UNIQUE INDEX uq_ai_task_intake_inflight
  ON ai_tasks (user_id)
  WHERE task_type = 'EXPERIENCE_INTAKE' AND status IN ('PENDING', 'RUNNING');

CREATE UNIQUE INDEX uq_ai_task_analysis_inflight
  ON ai_tasks (job_posting_id)
  WHERE task_type = 'POSTING_ANALYSIS' AND status IN ('PENDING', 'RUNNING');

-- ── 조회 최적화 인덱스 ───────────────────────────────────────
CREATE INDEX ix_job_matches_user_score
  ON job_matches (user_id, match_score DESC)
  INCLUDE (job_posting_id, verdict, covered_count);

CREATE INDEX ix_answers_used_experiences
  ON cover_letter_answers USING GIN (used_experience_ids);

-- ── updated_at 자동 갱신 (JPA에서 직접 채우면 생략) ─────────
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'competencies', 'experiences', 'companies', 'job_postings',
    'posting_competencies', 'job_posting_questions', 'job_matches', 'cover_letter_answers'
  ] LOOP
    EXECUTE format(
      'CREATE TRIGGER trg_%1$s_updated_at BEFORE UPDATE ON %1$I FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t);
  END LOOP;
END $$;
```
