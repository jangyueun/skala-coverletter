# ERD v2 → v3 최종 정리 (2026-09-03)

## 배경
`erd-v2-implementation-20260903.md`(P0~P2 반영, PostgreSQL 실행 검증 완료) 이후,
실제 서비스 요구사항과 대조하는 과정에서 5건의 추가 피드백이 나왔고 전부 반영해
v3로 정리함. 이 문서는 그 최종 결과와 근거를 기록한다.

## v2 → v3 변경 사항

| # | v2 상태 | 문제 제기 | v3 조치 |
|---|---|---|---|
| 1 | `auth_provider`(LOCAL/GOOGLE) + `user_auth_providers`(1:N) 구조 | 로그인·가입을 Slack 하나로만 운영하는데 다중 인증수단 대비 구조가 왜 있는지 | `user_auth_providers` 테이블·`auth_provider` enum 삭제. `slack_team_id`/`slack_user_id`를 `users`에 직접 저장, `(slack_team_id, slack_user_id)` UNIQUE. 같은 `slack_user_id`도 워크스페이스가 다르면 다른 사람이므로 조합키 필수 |
| 2 | 북마크 추가/해제가 `POST`/`DELETE` 2개 API | `bookmarks`는 존재 여부만 있는 단순 테이블인데 API를 나눌 이유가 있는지 | `PUT /api/postings/{id}/bookmark { bookmarked: bool }` 단일 토글 API로 통합. true→upsert, false→행 삭제 |
| 3 | `/api/applications`, `/api/experiences` 등이 최상위 경로 | 로그인 사용자 본인 데이터인데 마이페이지 하위로 안 묶인 이유 | `/api/mypage/applications`, `/api/mypage/experiences`, `/api/mypage/bookmarks`로 이동 |
| 4 | `applications.status` 존재 | 이 필드가 왜 필요하고 무엇을 의미하는지 확인 요청 | 파이프라인 단계 추적 + `submitted_at` CHECK 정합성의 기준값 + 마이페이지 집계(지원예정/진행중/합격 등) 용도임을 확인. 흐름: `PLANNED→WRITING→SUBMITTED→DOCUMENT_PASSED/FAILED→INTERVIEWING→FINAL_PASSED/FAILED`(또는 언제든 `CANCELLED`) |
| 5 | `cover_letter_answers`가 버전 이력(`version`, `is_final` 부분유니크) 관리 | 자소서는 최종본 하나만 남기면 됨 (임시저장 → 제출 흐름) | 문항당 1행(`question_id` UNIQUE)으로 단순화. `version`/`is_final`/`uq_answer_final` 삭제. `submitted`/`submitted_at` 컬럼 추가 |

### 5번 후속 — 제출 후 수정 잠금
"최종 제출 이후 수정 불가능하게 막을 것"이 후속 요구사항으로 추가됨.
- CHECK 제약으로는 불가능 (같은 행의 OLD 값과 비교가 필요한데 CHECK는 그 행 내부 값끼리만 비교 가능)
- `BEFORE UPDATE` 트리거 `prevent_submitted_answer_edit()`로 방어: `OLD.submitted = true`이면서 `content`/`char_count`/`byte_count`가 바뀌려는 UPDATE를 예외로 차단
- `submitted: true → false`(제출 취소) 자체는 트리거가 막지 않음 — 재작성 허용 여부는 별도 정책 확인 필요
- API 레이어에서도 `submitted=true`면 `PUT`을 즉시 409로 막아야 함 (트리거는 API 우회에 대한 최종 방어선 역할)

## 최종 산출물

| 파일 | 내용 |
|---|---|
| `career_fit_v3.dbml` | 위 변경 전부 반영한 물리 ERD. `@dbml/core`(dbdiagram.io와 동일 파서)로 직접 파싱 검증 완료 — 테이블 18개, 관계 23개, 문법 에러 0건 |
| `career_fit_constraints_v3.sql` | v2 대비 `chk_auth_password`(삭제된 테이블 소속) 제거, `chk_version_positive`/`uq_answer_final` 제거, `chk_answer_submitted_at` 및 `trg_prevent_submitted_answer_edit` 트리거 신규 추가 |

`ai_service.dbml`/`ai_service_constraints.sql`은 이번 라운드에서 변경 사항 없음 (요청 범위 밖, 별도 확인 필요 상태로 보류).

## API 명세 (v3)

### 인증
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/auth/slack/callback` | Slack OAuth 콜백. `(slack_team_id, slack_user_id)`로 조회, 없으면 자동 가입 |
| POST | `/api/auth/logout` | 로그아웃 |
| GET | `/api/users/me` | 내 정보 조회 |

### 마이페이지
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/mypage/experiences` | 내 경험 목록 |
| POST | `/api/mypage/experiences` | 경험 등록 |
| PUT | `/api/mypage/experiences/{id}` | 경험 수정 |
| DELETE | `/api/mypage/experiences/{id}` | 경험 삭제 (근거로 쓰인 경험은 소프트 삭제 검토) |
| GET | `/api/mypage/applications` | 내 지원 목록 |
| GET | `/api/mypage/bookmarks` | 내 북마크 목록 |

### 역량 사전
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/competencies` | 역량 목록 (카테고리 필터) |
| GET | `/api/competencies/search?q=` | 별칭 부분검색 (GIN 인덱스) |

### 공고
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/postings?active=true` | 활성 공고 목록 |
| GET | `/api/postings/{id}` | 공고 상세 |
| POST | `/api/postings/manual` | 수동 공고 등록 (수동 지원 전 선행 생성) |
| PUT | `/api/postings/{id}/bookmark` | 북마크 토글 (`{bookmarked: bool}`) |

### 매칭
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/postings/{id}/match` | 저장된 매칭 결과 조회 |
| POST | `/api/postings/{id}/match/refresh` | 재계산 요청 (202, `ai_tasks` MATCH 생성) |
| GET | `/api/matches/history?postingId=` | 매칭 이력 비교 |

### 지원 · 자소서
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/applications` | 지원 등록 |
| PATCH | `/api/applications/{id}/status` | 상태 변경 (파이프라인 단계 이동) |
| POST | `/api/applications/{id}/questions` | 문항 등록 |
| PUT | `/api/questions/{id}/answer` | 답변 임시저장 (제출 후 호출 시 409) |
| PATCH | `/api/questions/{id}/answer/submit` | 최종 제출 (`submitted=true`, 이후 DB 트리거로 수정 차단) |
| POST | `/api/questions/{id}/draft` | AI 초안 생성 요청 (202, `ai_tasks` DRAFT 생성) |
| GET | `/api/answers/{id}/requirements` | 요구사항 충족 여부 |

이 API 목록은 실제 `render.js`가 아니라 v3 스키마 기반 역산 설계이므로, 실 코드와의 최종 대조가 별도로 필요함 (1차 리뷰 P3 항목 연장선).

## 확인/보류 상태로 남은 항목

1. **AI Service DB(`ai_service.dbml`)** — 이번 라운드 검토 범위 밖. 사용자 본인도 "모른다"고 확인, 추후 별도 확인 필요.
2. **제출 취소(재작성) 허용 여부** — 현재 트리거는 `submitted:true→false` 전환 자체는 막지 않음. 영구 잠금으로 갈지 결정 필요.
3. **LOCAL 인증 병행 여부** — Slack 전용으로 확정했으나, 추후 이메일/비번 로그인을 추가할 가능성이 있는지는 별도 확인 안 됨.
4. **dbdiagram.io 렌더링 실패 원인 미상** — `@dbml/core` 파싱은 정상 통과(에러 0건), 무료 플랜 테이블 개수 제한도 없음을 확인함. 원인이 복붙 누락인지 사이트/브라우저 이슈인지는 에러 메시지 확보 전까지 미상.
