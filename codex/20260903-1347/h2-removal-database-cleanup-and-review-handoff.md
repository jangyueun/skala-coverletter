# H2 제거·기존 DB 확인 및 외부 리뷰 인계서

작성일: 2026-09-03 13:47 KST

## 1. 이번 요청의 결론

- Career Fit 백엔드는 H2를 사용하지 않는다.
- 로컬·배포 모두 제공받은 PostgreSQL에 JDBC로 직접 연결한다.
- 스키마 생성과 변경은 Flyway가 담당한다.
- Hibernate는 `ddl-auto:validate`로 엔티티와 스키마가 맞는지만 확인한다.
- 로컬 PostgreSQL에서 Career Fit 또는 이전 검증용으로 식별되는 DB는 발견되지 않아 삭제할 DB가 없었다.
- 다른 프로젝트로 확인되는 DB와 스키마는 삭제하지 않았다.

## 2. H2 잔여물 검사 결과

실제 실행 설정과 의존성에는 H2가 없었다. 다만 `docs/slack-oauth.md`의 과거 의존성 예시에
`com.h2database:h2` 한 줄이 남아 있어 제거했다. 같은 문서의 의존성 예시는 현재 구성에 맞게
Flyway starter, Flyway PostgreSQL 모듈, PostgreSQL JDBC 드라이버로 변경했다.

워크스페이스에는 `*.mv.db`, `*.h2.db`, `*.trace.db`, `h2*.jar` 파일도 존재하지 않았다.

전체 검색 중 다음 항목은 H2 데이터베이스와 무관하므로 수정하지 않았다.

- `mock/*.html`, `mock/src/*.js`의 `<h2>`: HTML 제목 태그
- SQL 테스트 데이터의 `'h2'`: 테스트용 `input_hash` 문자열
- 이전 검토 문서의 `H2 설정은 잘못됐다`는 설명: H2를 사용하지 말아야 한다는 결정 기록

검사 기준 문자열:

```text
com.h2database
jdbc:h2
h2database
MODE=PostgreSQL
```

## 3. PostgreSQL DB 확인 및 삭제 결과

로컬 PostgreSQL의 비템플릿 DB 목록은 다음과 같았다.

```text
choiminseok
postgres
school_db
skala_db
```

Career Fit 관련 이름(`career`, `codex`, `claude`, `ai_service`)을 가진 DB는 없었다.
앞선 Flyway 검증에 사용한 `codex_flyway_boot_verify_20260903` DB도 이미 테스트 종료 시 삭제된 상태다.

- `postgres` DB에는 별도 전자상거래 실습 스키마가 있다.
- `skala_db`에는 `lab` 실습 테이블이 있다.
- `school_db`는 이름상 다른 프로젝트 DB다.
- `choiminseok` 기본 DB에는 사용자 테이블이 확인되지 않았다.

따라서 이번 작업에서는 실제 DB를 추가로 삭제하지 않았다. 프로젝트와 무관한 DB를 이름만 보고 삭제하는 것은
데이터 손실 위험이 있기 때문이다. 공유 원격 PostgreSQL에도 삭제 명령을 실행하지 않았다.

## 4. 현재 반영된 핵심 파일

### 애플리케이션 설정

`backend/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/careerfit}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
    clean-disabled: true
```

`baseline-on-migrate:false`는 기존 테이블을 Flyway 적용 완료 상태로 임의 간주하지 않기 위한 안전장치다.
`clean-disabled:true`는 애플리케이션에서 공유 DB를 실수로 초기화하지 못하게 한다.

### 의존성

`backend/build.gradle`

```gradle
implementation 'org.springframework.boot:spring-boot-starter-flyway'
implementation 'org.flywaydb:flyway-database-postgresql'
runtimeOnly 'org.postgresql:postgresql'
```

H2 의존성은 없다.

### 최초 스키마

`backend/src/main/resources/db/migration/V1__create_career_fit_schema.sql`

- Slack 사용자는 `(slack_team_id, slack_user_id)`로 유일하게 식별한다.
- enum 성격 컬럼은 JPA `EnumType.STRING`과 맞도록 `varchar + CHECK`로 저장한다.
- 비활성 공고 문항을 보존할 수 있도록 활성 문항에만 공고·순번 부분 유니크 인덱스를 적용한다.
- CHECK, 외래키, 인덱스, `updated_at` 트리거를 Flyway 이력에 포함한다.

## 5. 검증 완료 항목

- Gradle 전체 테스트 통과
- PostgreSQL 17 빈 임시 DB에서 Flyway V1 적용 성공
- Flyway 적용 후 Hibernate `ddl-auto:validate` 성공
- 테스트용 임시 DB 삭제 완료
- 공유 원격 PostgreSQL 미변경

## 6. 다른 에이전트에게 요청할 리뷰 항목

다음 질문을 중심으로 독립 검토를 요청한다.

1. `V1__create_career_fit_schema.sql`이 현재 Slack 인증 ERD와 일치하는가?
2. PostgreSQL `varchar + CHECK` 방식이 향후 JPA enum 엔티티와 충돌하지 않는가?
3. `job_posting_questions`의 활성 행 부분 유니크 인덱스가 `is_active=false` 이력 보존 정책을 만족하는가?
4. `answer_requirement_results`가 실제 MVP 화면/API에 필요하지 않다면 V2에서 제거할지 결정해야 하는가?
5. 이미 테이블이 존재하는 공유 원격 DB를 도입할 경우, 삭제 초기화와 보존 전환 중 어떤 절차가 필요한가?
6. AI Service DB도 별도 Flyway 프로젝트와 별도 migration history로 관리하도록 구성돼 있는가?
7. API 명세의 최종 URL과 Career DB FK·유니크 키가 같은 자원 경계를 표현하는가?

## 7. 리뷰 시 주의할 점

- 적용된 V1은 수정하지 않고 변경 사항은 V2 이후 파일로 추가한다.
- 공유 DB에 데이터가 있으면 스키마를 삭제하지 않는다.
- `baseline-on-migrate:true`로 단순 우회하면 V1이 건너뛰어질 수 있으므로 사용하지 않는다.
- DBML export SQL과 Flyway SQL을 중복 적용하지 않는다.
- AI Service DB는 Career DB와 물리 FK를 맺지 않고 ID 값만 논리 참조한다.
