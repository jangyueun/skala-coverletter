# Slack 인증 확정 및 Claude 리뷰 항목 설명

## 1. 인증 최종 판단

현재 MVP는 Slack OAuth + 서버 세션을 사용한다. 따라서 최종 ERD의 `users.supabase_user_id`와 API 문서의 Supabase Bearer Token 설명은 현재 구현과 맞지 않으므로 제거해야 한다.

현재 MVP의 users 권장 구조:

```dbml
Table users {
  id             bigint       [pk, increment]
  slack_team_id  varchar(32)  [not null]
  slack_user_id  varchar(32)  [not null]
  display_name   varchar(100) [not null]
  email          varchar(320)
  avatar_url     varchar(1000)
  created_at     timestamptz  [not null, default: `now()`]
  last_login_at  timestamptz  [not null, default: `now()`]

  Indexes {
    (slack_team_id, slack_user_id) [unique]
  }
}
```

API도 현재 구현에 맞춘다.

- 로그인 시작: `GET /api/auth/slack/start`
- Slack 콜백: `GET /api/auth/slack/callback`
- 로그인 상태: `GET /api/auth/me`
- 로그아웃: `POST /api/auth/logout`
- 인증 방식: 서버 세션 쿠키
- `/api/auth/me` 미로그인 응답: 현재 구현 계약대로 `200 + null`

향후 Spring Security에서 JWT를 발급하더라도 JWT는 “로그인 제공자”가 아니라 인증 결과를 전달하는 토큰 형식이다. JWT의 `sub`에 내부 `users.id`를 넣으면 도메인 ERD는 바뀌지 않는다. 자체 이메일/비밀번호 로그인까지 추가할 때만 별도의 자격 증명 또는 인증 제공자 구조를 다시 검토한다.

## 2. 전역 UNIQUE와 is_active 위치

Claude DBML의 `job_posting_questions` 안에 둘 다 선언돼 있다.

```dbml
is_active boolean [not null, default: true,
  note: '답변 이력 보존을 위해 수집 갱신 시 hard delete 대신 비활성화']

Indexes {
  (job_posting_id, sequence) [unique]
  (job_posting_id, is_active)
}
```

- `is_active` 보존 정책: `career_fit_final.dbml` 226행
- 전역 UNIQUE: 같은 파일 231행
- 실제 생성 SQL의 전역 UNIQUE: `career_fit_final.generated.sql` 254행
- 생성 SQL에 기록된 보존 정책 설명: 같은 파일 294행

여기서 전역 UNIQUE란 `is_active=true/false`를 구분하지 않고 테이블 전체 행을 검사한다는 뜻이다.

문제 예:

```text
공고 10 / 순번 1 / 옛 질문 / is_active=false
공고 10 / 순번 1 / 새 질문 / is_active=true
```

두 행은 보존 정책상 함께 있어야 하지만 `(10, 1)`이 중복이므로 현재 UNIQUE가 신규 INSERT를 막는다.

수정:

```sql
CREATE UNIQUE INDEX uq_job_posting_question_active_sequence
  ON job_posting_questions (job_posting_id, sequence)
  WHERE is_active = true;
```

DBML의 기존 `[unique]`는 제거하고 위 부분 유니크 인덱스는 constraints SQL에 둔다.

## 3. MATCH 단일 실행 문제

현재 AI constraints에는 PENDING/RUNNING MATCH를 사용자+공고당 하나만 허용하는 인덱스가 있다.

```sql
CREATE UNIQUE INDEX uq_ai_task_match_inflight
  ON ai_tasks (external_user_id, external_job_posting_id)
  WHERE task_type = 'MATCH'
    AND status IN ('PENDING', 'RUNNING');
```

목적은 같은 매칭이 동시에 여러 번 실행되는 것을 방지하는 것이다. 하지만 기존 MATCH가 실행 중일 때 사용자가 경험을 수정하면 최신 경험 기준 새 MATCH가 막힐 수 있다.

```text
경험 상태 A → MATCH 실행 중
경험 상태 B로 수정 → 새 MATCH가 UNIQUE로 거절
기존 MATCH 완료 → A 기준 점수가 저장
결과적으로 B가 반영되지 않음
```

권장 MVP 방식:

- DRAFT 동시 실행 방지 인덱스는 유지
- MATCH 동시 실행 방지 인덱스는 제거
- MATCH 결과 저장 전에 현재 경험 `input_hash`와 작업의 `input_hash`를 비교
- 다르면 오래된 결과를 버리고 최신 입력으로 다시 요청

## 4. DDL/constraints SQL과 ddl-auto 문제

Claude의 검증은 다음 순서로 SQL을 직접 적용했을 때 성공했다.

```text
generated.sql
→ constraints.sql
→ smoke_test.sql
```

하지만 현재 백엔드는 `spring.jpa.hibernate.ddl-auto=update`다. Hibernate가 엔티티로 테이블만 만들면 별도 constraints SQL에 있는 다음 항목은 자동으로 생기지 않는다.

- CHECK 제약
- 부분 유니크 인덱스
- `updated_at` 트리거
- `pg_trgm` 인덱스

따라서 “SQL 테스트가 통과했다”와 “현재 애플리케이션 DB에도 같은 제약이 있다”는 다른 말이다.

권장:

- DBML은 설계/다이어그램으로 사용
- 실제 DB 변경은 Flyway SQL로 적용
- `ddl-auto=validate` 사용

MVP 기간 때문에 `update`를 유지한다면 최소한 DB 초기화 스크립트가 `generated.sql → constraints.sql`을 항상 실행하도록 통일해야 한다.

## 5. PostgreSQL ENUM과 JPA ENUM 문제

DBML에서 export한 SQL은 `CREATE TYPE ... AS ENUM`으로 PostgreSQL 고유 ENUM 타입을 만든다. JPA의 `@Enumerated(EnumType.STRING)`은 일반적으로 문자열 컬럼을 기대한다.

둘을 별도 설정 없이 섞으면 INSERT/UPDATE 시 타입 캐스팅 오류가 생길 수 있다.

초보 팀 권장:

- 실제 SQL에서는 `varchar` 컬럼 사용
- 허용 값은 CHECK 제약으로 제한
- Java에서는 `@Enumerated(EnumType.STRING)` 사용

PostgreSQL 네이티브 ENUM을 유지하려면 Hibernate의 named enum 매핑을 별도로 구성해야 한다.

## 6. checks 블록 설명

Claude가 CHECK를 별도 SQL로 옮긴 결과는 사용할 수 있다. 그러나 `checks {}` 자체가 DBML 비지원 문법이라는 설명은 현재 공식 문서와 맞지 않는다.

`checks {}`는 공식 DBML 문법이다. 원본 전체 파일에서 파싱 오류가 났다면 실제 파서 실행 방법, 파일 문맥 또는 사용한 패키지를 다시 확인해야 한다.

이 문제는 DB 구조가 잘못됐다는 뜻은 아니다. 현재처럼 DBML에는 설명을 남기고 실제 제약을 SQL로 관리해도 된다.

## 7. requirements/addressed 의미

`job_posting_questions.requirements`는 한 문항 안의 세부 요구사항이다.

예:

```json
[
  {"seq": 1, "content": "개선한 대상"},
  {"seq": 2, "content": "수치로 확인한 결과"}
]
```

`answer_requirement_results.addressed`는 현재 답변이 각 요구사항을 충족했는지 저장한다.

현재 최종 화면과 API에는 이 결과를 보여주는 부분이 없다. 사용하지 않으면 두 구조를 제거하는 것이 단순하다. AI가 “요구사항 충족/미충족”을 화면에 표시할 계획이면 유지하고 API 응답에도 포함해야 한다.

## 8. 문항 추출 AI 작업 유형 의미

공고 문항에는 `source_ai_task_id`가 있지만 AI 작업 enum에는 별도 `QUESTION_EXTRACTION`이 없다.

가능한 방식:

1. 공고 원문 분석 한 번으로 요구 역량과 자소서 문항을 함께 추출
2. 역량 추출과 문항 추출을 별도 AI 작업으로 분리
3. 문항은 AI가 아니라 크롤러/파서가 수집

현재 팀 규모에는 1번을 권장한다. 이 경우 작업 이름을 `POSTING_COMPETENCY_EXTRACTION`보다 `POSTING_ANALYSIS`로 바꾸는 편이 명확하다.

## 9. NOT_COMPUTED 의미

다음 세 상태를 구분하기 위한 값이다.

- `NOT_COMPUTED`: 계산 요청 자체가 없었음
- `PENDING/RUNNING`: 계산 요청은 있고 처리 중
- `FAILED`: 계산했지만 실패
- `COMPLETED`: 계산 완료

이 구분이 없으면 경험이 없는 사용자가 공고에 들어갔을 때 화면이 계속 “계산 중”으로 표시될 수 있다. 추가 자체는 타당하다.

## 10. Outbox 없는 비동기 호출 위험

현재 방식은 다음 두 동작 사이에서 서버가 종료될 수 있다.

```text
경험 DB 저장 성공
→ AI Service 호출
```

DB 저장 직후 서버가 죽으면 경험은 저장됐지만 매칭 요청은 전달되지 않는다. Kafka가 꼭 필요하다는 뜻은 아니다.

MVP 보완 방법:

- 공고 목록/매칭 조회 시 현재 경험 해시와 `job_matches.input_hash`가 다르면 재계산 요청
- 또는 일정 주기의 간단한 재검사 작업 사용

## 11. 테스트 범위 설명

Claude의 테스트는 실제로 통과했다. 다만 전체 규칙을 전부 검사한 것은 아니다. 특히 다음 테스트를 추가해야 한다.

- 비활성 문항 뒤 동일 순번 새 문항 등록
- MATCH 실행 중 경험 재수정
- 다른 사용자의 경험을 답변 근거로 연결하는 요청 차단
- 모든 작업 유형의 대상 ID 조합
- PostgreSQL ENUM과 실제 JPA Entity 저장 통합 테스트

## 우선순위

### 지금 수정

1. Supabase 설명을 제거하고 Slack 사용자/세션 ERD·API로 변경
2. 활성 공고 문항 부분 유니크로 변경
3. MATCH 최신 경험 변경 누락 방지
4. 실제 스키마 적용 방식을 결정

### 팀 기능 확인 후 결정

5. requirements/addressed 유지 여부
6. 공고 분석 작업이 역량+문항을 함께 추출하는지 여부
7. 경험 삭제를 추가할 때 소프트 삭제 도입

### 그대로 유지 가능

8. DRAFT 동시 실행 방지
9. 작업 유형별 필수 대상 CHECK
10. `NOT_COMPUTED` 응답 상태
