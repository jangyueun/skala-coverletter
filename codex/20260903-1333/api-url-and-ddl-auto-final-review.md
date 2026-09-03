# Java 컨벤션 기준 DB 설정 및 API URL 최종 수정안

검토일: 2026-09-03  
인증: Slack OAuth + 서버 세션  
DB: Supabase가 제공하는 PostgreSQL만 사용(Supabase Auth 미사용)

## 1. ddl-auto 결정 (정정)

공유 PostgreSQL에서는 `ddl-auto:update`를 사용하지 않는다. 삭제·이름 변경된 컬럼과 테이블이 남고,
팀원별 실행 순서에 따라 스키마가 달라질 수 있기 때문이다. Flyway가 스키마를 변경하고 Hibernate는 검증만 한다.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
    clean-disabled: true
```

규칙:

1. DBML은 관계 확인과 문서화에 사용하고, 실제 DB 변경은 Flyway SQL만 사용한다.
2. 적용된 마이그레이션은 수정하지 않고 다음 버전 파일을 추가한다.
3. CHECK와 부분 유니크 인덱스도 같은 Flyway 파일에서 관리한다.
4. `flyway clean`과 공유 DB 자동 초기화는 금지한다.
5. 이미 테이블이 있는 공유 DB는 임의로 baseline 처리하지 않는다. 데이터 보존 여부를 확인한 뒤 전환용 마이그레이션을 작성한다.

## 2. PostgreSQL 연결 설정

Supabase는 인증이 아니라 외부 PostgreSQL 제공자로만 사용한다. 애플리케이션에는 다음 값만 환경변수로 제공한다.

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/careerfit}
    driver-class-name: org.postgresql.Driver
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:}
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:5}
```

원격 PostgreSQL URL에는 일반적으로 `sslmode=require`가 필요하다. 실제 호스트, 사용자명, 비밀번호는 커밋하지 않는다.

기존 `application-local.yml.example`의 H2 설정은 다음 이유로 잘못됐다.

- 실제 목표 DB가 PostgreSQL이다.
- `build.gradle`에 H2 드라이버 의존성이 없다.
- H2의 PostgreSQL 호환 모드는 PostgreSQL ENUM, 부분 인덱스, `pg_trgm`, JSONB 동작을 동일하게 검증하지 못한다.

## 3. Slack 인증 API

현재 구현된 URL을 최종 명세로 사용한다.

| Method | URL | 의미 |
|---|---|---|
| GET | `/api/auth/slack/start?returnTo=/` | Slack 로그인 시작, 302 |
| GET | `/api/auth/slack/callback?code=...&state=...` | Slack 콜백, 사용자 upsert와 세션 생성, 302 |
| GET | `/api/auth/me` | 로그인 상태 조회 |
| POST | `/api/auth/logout` | 세션 폐기, 204 |

현재 구현 계약에 따라 `/api/auth/me`는 미로그인 시 `200 OK`와 JSON `null`을 반환한다. 다른 보호 API는 미로그인 시 `401`을 반환한다.

브라우저 프론트는 교차 origin 개발 환경에서 세션 쿠키를 보내도록 요청에 credentials 옵션을 사용해야 한다.

```javascript
fetch(url, { credentials: 'include' })
```

## 4. 잘못됐거나 부적합한 기존 URL

| 기존 URL | 문제 | 최종 URL |
|---|---|---|
| `GET /api/me` | 현재 인증 컨트롤러와 불일치, auth 그룹에서 벗어남 | `GET /api/auth/me` |
| `GET /api/postings/{postingId}/application-questions` | applications를 제거했는데 이름에 application이 남음 | `GET /api/postings/{postingId}/questions` |
| `POST /api/posting-questions/{questionId}/drafts` | 공고 하위 조회 URL과 단건 조작 URL의 자원 이름이 불일치 | `POST /api/questions/{questionId}/drafts` |
| `GET /api/posting-questions/{questionId}/answer` | 동일 | `GET /api/questions/{questionId}/answer` |
| `PUT /api/posting-questions/{questionId}/answer` | 동일 | `PUT /api/questions/{questionId}/answer` |
| `GET /api/applications/{applicationId}/assessment` | application 엔티티 제거, 매칭 단위도 사용자+공고 | `GET /api/postings/{postingId}/match` |
| `POST /api/applications` | 최종 화면에 지원서 생성 단계 없음 | 삭제 |
| `POST /api/applications/{applicationId}/questions` | 문항은 공고 소속 | 삭제 |
| `GET/POST /api/answers/{answerId}/versions` | 버전 기능 제거 | 삭제 |
| `POST /api/answers/{answerId}/drafts` | 초안 생성 전 answerId가 없음 | `POST /api/questions/{questionId}/drafts` |
| `GET /api/mock-jobs/{jobId}` | mock 이름과 job/task 용어 혼용 | `GET /api/ai-tasks/{taskId}` |
| `PUT /api/postings/{postingId}/competencies` | 일반 사용자가 공용 공고 역량을 바꿀 수 있는 공개 URL처럼 보임 | `PUT /internal/postings/{postingId}/competencies` |

`application-questions`라는 표현 자체가 영어 의미상 틀린 것은 아니지만, 이 프로젝트에서는 applications 테이블과 API를 제거했기 때문에 개발자가 관계를 오해할 가능성이 크다. `/questions`로 단순화한다.

## 5. 최종 API URL 목록

### Auth — 4개

```http
GET  /api/auth/slack/start
GET  /api/auth/slack/callback
GET  /api/auth/me
POST /api/auth/logout
```

### Competency — 1개

```http
GET /api/competencies
```

### Posting — 5개

```http
GET /api/postings
GET /api/postings/{postingId}
GET /api/postings/{postingId}/questions
GET /api/postings/{postingId}/match
PUT /api/postings/{postingId}/bookmark
```

### Experience — 4개

```http
GET  /api/experiences
POST /api/experiences
PUT  /api/experiences/{experienceId}
POST /api/experience-intakes
```

### Question/Answer — 3개

```http
POST /api/questions/{questionId}/drafts
GET  /api/questions/{questionId}/answer
PUT  /api/questions/{questionId}/answer
```

### AI task — 1개

```http
GET /api/ai-tasks/{taskId}
```

### Internal — 2개

```http
POST /internal/mock/competency-extractions
PUT  /internal/postings/{postingId}/competencies
```

합계는 Auth 포함 20개다. 개발용 mock endpoint를 실제 배포에서 비활성화하면 운영 노출 API는 19개다.

## 6. Query Parameter 표기

Spring MVC에서 역량 여러 개를 받을 때 문서에 `competencyId[]`로 쓰지 않고 같은 파라미터를 반복한다.

```http
GET /api/experiences?competencyId=1&competencyId=3&sort=latest&size=20
```

Controller 예:

```java
@RequestParam(required = false) List<Long> competencyId
```

공고 목록은 기본적으로 ACTIVE만 반환하므로 목업의 `/api/postings?active=true`는 `/api/postings`로 단순화한다. 종료 공고까지 조회해야 할 관리 기능이 생길 때만 별도 status 필터를 추가한다.

## 7. Java 컨벤션과 API 응답의 충돌

컨벤션은 성공 시 `{"data": ...}` 같은 공통 껍데기를 사용하지 않는다.

- 단건: DTO 객체 직접 반환
- 일반 목록: 배열 직접 반환
- 커서 페이지: `postings`, `nextCursor`, `hasNext`를 가진 의미 있는 Page DTO 허용
- 실패: `{"message":"..."}`만 반환

기존 API 문서의 `INVALID_QUERY_PARAMETER`, `EXPERIENCE_NOT_FOUND` 같은 문자열은 구현팀 내부 식별 이름으로는 사용할 수 있지만, 현재 컨벤션대로라면 응답 Body의 `code` 필드로 보내지 않는다.

예:

```http
HTTP/1.1 404 Not Found
Content-Type: application/json
```

```json
{
  "message": "경험을 찾을 수 없습니다."
}
```

프론트가 오류 코드를 반드시 필요로 한다면 먼저 컨벤션을 `{"code":"...","message":"..."}`로 팀 합의해 변경한 뒤 모든 API에 일괄 적용해야 한다.

## 8. 요청 검증 의존성

컨벤션은 요청 DTO 검증을 사용하도록 명시하지만 기존 `build.gradle`에는 Validation 의존성이 없었다. 다음 의존성이 필요하다.

```gradle
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

Controller에서는 요청 DTO에 `@Valid`, record 필드에 `@NotBlank`, `@NotNull`, `@Size`, 범위 검증을 사용한다. 사용자 소유권과 같은 업무 규칙은 Service에서 확인한다.

## 9. JPA ENUM 규칙

Java enum은 JPA와 PostgreSQL 타입 검증이 단순하도록 문자열로 저장한다.

```java
@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false, length = 30)
private AiTaskStatus status;
```

DBML의 Enum은 다이어그램상의 허용 값 설명으로 사용한다. DB에는 `varchar`와 CHECK 제약을 Flyway로 만든다.

## 10. 최종 적용 순서

1. 외부 PostgreSQL 연결 환경변수 설정
2. 빈 신규 DB에서 애플리케이션을 실행해 Flyway `V1` 적용
3. Hibernate `validate` 통과 확인
4. Slack 로그인 API 확인
5. 이후 ERD 변경마다 `V2`, `V3` 마이그레이션 추가

기존 테이블이 있는 공유 DB는 바로 실행하지 않는다. `baseline-on-migrate:false`가 안전하게 실행을 막도록 두고,
데이터가 불필요한지 또는 보존해야 하는지를 팀이 먼저 결정한다.
