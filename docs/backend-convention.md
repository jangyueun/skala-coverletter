# Backend Coding Convention

Spring Boot(Java 21) 기준입니다. 지금 `backend/` 에 있는 `auth` · `user` · `global` 코드가
이 문서의 기준 예시입니다. 새로 쓰는 코드는 그 파일들과 같은 모양이면 됩니다.

## 1. 기본 원칙

- **읽는 사람 기준으로 씁니다.** 3일짜리 프로젝트지만 서로의 코드를 읽어야 합니다.
- **왜 그렇게 했는지를 남깁니다.** 코드가 무엇을 하는지는 코드가 말합니다. 주석은 이유를 씁니다.
- **비밀값은 코드에 두지 않습니다.** Client Secret, API Key, 비밀번호는 환경변수나 gitignore된 `.env`로만 넣습니다.
- **Lombok을 쓰지 않습니다.** 지금 코드에 없습니다. 생성자와 getter는 직접 씁니다.
- **의문이 생기면 담당자에게 묻고 정합니다.** 혼자 다른 방식으로 만들지 않습니다.

## 2. 패키지 구조

도메인별로 나누고, 도메인 안에서 계층별로 나눕니다.

```text
com.team.careerfit
├── auth          로그인
├── user          사용자
├── coverletter   자기소개서
├── experience    경험
├── matching      공고 매칭
└── global        여러 도메인이 함께 쓰는 것
    ├── config
    ├── exception
    └── security
```

각 도메인 패키지는 아래 다섯 개를 가집니다.

| 패키지 | 넣는 것 | 예시 |
| --- | --- | --- |
| `controller` | HTTP 요청 처리 | `SlackAuthController` |
| `service` | 비즈니스 로직 | `SlackLoginService` |
| `repository` | DB 접근 | `UserRepository` |
| `entity` | JPA 엔티티 | `User` |
| `dto` | 요청·응답 객체 | `UserResponse` |

- **다른 도메인의 `entity` 나 `repository` 를 직접 쓰지 않습니다.** 필요하면 그 도메인의 `service` 를 통해 씁니다.
- 두 도메인 이상이 함께 쓰는 것만 `global` 에 둡니다. 애매하면 도메인 안에 둡니다.
- 예외 **클래스**는 도메인 안에 두지만, 예외 **핸들러**는 `global/exception` 에 하나만 둡니다 (7절).

## 3. 이름 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 클래스 | `UpperCamelCase` | `SlackLoginService` |
| 메서드·변수 | `lowerCamelCase` | `completeLogin`, `slackTeamId` |
| 상수 | `UPPER_SNAKE_CASE` | `AUTHORIZE_URL`, `USER_ID` |
| 패키지 | 소문자, 붙여쓰기 | `coverletter` (❌ `coverLetter`) |
| DB 테이블·컬럼 | `snake_case` | `users`, `slack_team_id` |
| API 경로 | 소문자 + 하이픈 | `/api/cover-letters` |

클래스 이름은 계층 이름으로 끝냅니다 — `~Controller`, `~Service`, `~Repository`, `~Response`, `~Request`, `~Exception`.

API 경로는 **자원을 다루면 복수형 명사**를 씁니다.

```text
GET    /api/cover-letters        목록
POST   /api/cover-letters        생성
GET    /api/cover-letters/{id}   단건
```

자원 조작이 아닌 동작은 그대로 동사를 씁니다 — `/api/auth/logout`, `/api/auth/slack/callback` 처럼요.
억지로 복수형을 붙이지 않습니다.

메서드 이름은 하는 일을 그대로 씁니다. `get~` 은 getter에만 씁니다.

```java
public User completeLogin(String code, String codeVerifier)   // 좋음
public User process(String a, String b)                       // 나쁨
```

## 4. 코드 스타일

- 들여쓰기 **스페이스 4칸**. 탭을 쓰지 않습니다.
- 줄 바꿈한 다음 줄은 **8칸** 들여씁니다.
- 한 줄은 **120자**를 넘기지 않습니다.
- `import` 는 **와일드카드(`*`)를 쓰지 않습니다.** static import를 먼저, 그다음 일반 import를 알파벳순으로 둡니다.
- 중괄호는 한 줄짜리 `if` 에도 붙입니다.
- 파일 끝에 빈 줄 하나를 둡니다.

IntelliJ 사용자는 `Settings → Editor → Code Style → Java` 에서 Indent 4 / Continuation indent 8 / Hard wrap 120 으로 맞추고,
저장 전에 `Ctrl+Alt+L`(Mac은 `Cmd+Opt+L`) 로 포맷합니다.

## 5. 계층별 규칙

### 의존성 주입은 생성자로

`@Autowired` 필드 주입을 쓰지 않습니다. 필드는 `private final` 로 두고 생성자로 받습니다.

```java
@Service
public class SlackLoginService {

    private final SlackOAuthClient slackOAuth;
    private final UserRepository users;

    public SlackLoginService(SlackOAuthClient slackOAuth, UserRepository users) {
        this.slackOAuth = slackOAuth;
        this.users = users;
    }
}
```

### Controller

- `@RestController` + 클래스에 `@RequestMapping("/api/...")`, 메서드에 `@GetMapping` / `@PostMapping`.
- 반환 타입은 `ResponseEntity<...>` 를 씁니다.
- **비즈니스 로직을 넣지 않습니다.** 요청 값을 꺼내 서비스에 넘기고, 결과를 응답으로 바꾸는 데까지입니다.
- **엔티티를 그대로 반환하지 않습니다.** 항상 DTO로 바꿔서 내보냅니다.

DTO로 바꾸는 위치에 주의합니다. `application.yml` 에 `open-in-view: false` 로 두었기 때문에
**컨트롤러에는 영속성 컨텍스트가 없습니다.** 지연 로딩된 연관 필드를 컨트롤러에서 건드리면
`LazyInitializationException` 이 나고 500이 됩니다.

- 연관 엔티티를 쓰는 응답은 **트랜잭션이 살아 있는 서비스 안에서** DTO까지 만들어 반환합니다.
- 연관 없이 자기 필드만 쓰는 응답은 컨트롤러에서 변환해도 됩니다 (`MeController` 가 그렇습니다).

**성공 응답은 DTO를 그대로 내보냅니다.** `{"data": ...}` 같은 껍데기를 씌우지 않습니다.
목록은 배열 그대로, 단건은 객체 그대로입니다. 성공과 실패는 상태 코드로 구분하고,
실패일 때만 `{"code": "...", "message": "..."}` 형식을 씁니다 (7절).

```java
return ResponseEntity.ok(CoverLetterResponse.from(saved));   // 좋음
return ResponseEntity.ok(Map.of("data", saved));             // 나쁨
```

> 프론트와 맞물리는 부분입니다. 다르게 갈 거면 **프론트 담당자와 먼저 합의**하고 이 문서를 고칩니다.

### Service

- `@Service` 를 붙입니다. 실제 로직은 여기에 있습니다.
- DB를 쓰는 메서드에 `@Transactional` 을 붙입니다. 읽기만 하면 `@Transactional(readOnly = true)` 입니다.
- `HttpServletRequest` · `HttpSession` 같은 웹 타입을 서비스에서 다루지 않습니다. 그건 컨트롤러의 몫입니다.

**변경 감지(dirty checking)를 씁니다.** `@Transactional` 안에서 조회한 엔티티는 값만 바꾸면
트랜잭션이 끝날 때 자동으로 UPDATE 됩니다. `save()` 를 다시 부르지 않습니다.

```java
@Transactional
public User completeLogin(String code, String codeVerifier) {
    ...
    return users.findBySlackTeamIdAndSlackUserId(profile.teamId(), profile.userId())
            .map(existing -> {
                existing.syncFromSlack(displayName, profile.email(), profile.avatarUrl());
                return existing;                       // save() 없음 — 버그가 아닙니다
            })
            .orElseGet(() -> users.save(User.firstLogin(...)));   // 새 엔티티만 save()
}
```

반대로 **트랜잭션 밖에서 바꾼 값은 반영되지 않습니다.** 값을 바꾸는 코드는 `@Transactional` 안에 두세요.

### Repository

- `JpaRepository<엔티티, ID>` 를 상속한 인터페이스로 만듭니다.
- 메서드 이름으로 쿼리를 만드는 것을 우선합니다. 복잡해지면 `@Query` 를 씁니다.

### Entity

- `@Entity` + `@Table(name = "...")`. 테이블 이름은 복수형입니다.
- **PK를 제외한** 모든 필드에 `@Column(name = "...")` 으로 컬럼 이름을 명시합니다. `nullable`, `length` 도 같이 적습니다.
- **setter를 만들지 않습니다.** 값을 바꿔야 하면 뜻이 담긴 메서드를 만듭니다 (`syncFromSlack(...)`).
- 생성은 `private` 생성자 + `public static` 팩토리 메서드로 합니다 (`User.firstLogin(...)`).
- JPA용 기본 생성자는 `protected` 로 두고 `// JPA 용` 주석을 답니다.

**연관관계는 반드시 지연 로딩입니다.**

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

- `@ManyToOne` · `@OneToOne` 의 기본값은 **EAGER** 입니다. `fetch = FetchType.LAZY` 를 빠뜨리면
  자기소개서 하나를 읽을 때 사용자 테이블이 매번 따라 붙습니다. 목록 조회에서는 N+1이 됩니다.
- **단방향 `@ManyToOne` 을 기본으로 합니다.** 양방향(`@OneToMany` 를 반대편에 두는 것)은 정말 필요할 때만 만듭니다.
- `cascade` 와 `orphanRemoval` 은 주인 없이 존재할 수 없는 자식에만 씁니다. `User` 쪽에는 걸지 않습니다.

**시간은 `Instant` 로 통일합니다.** `LocalDateTime` 을 섞지 않습니다 — 타임존이 빠져 있어서
로컬과 배포 환경의 값이 달라집니다.

- `createdAt` 에는 `updatable = false` 를 붙입니다.
- 생성·수정 시각은 팩토리 메서드와 변경 메서드 안에서 **직접 `Instant.now()` 로 채웁니다** (`User` 참고).
  JPA Auditing(`@EnableJpaAuditing` · `@CreatedDate`)은 쓰지 않습니다. 도입하려면 팀에서 합의하고
  다섯 도메인을 한꺼번에 바꿉니다. 반씩 섞이는 게 제일 나쁩니다.

### DTO

- **`record` 로 만듭니다.**
- 엔티티 → DTO 변환은 DTO 쪽의 `static from(...)` 에 둡니다.

```java
public record UserResponse(Long id, String displayName, String email, String avatarUrl) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getDisplayName(), user.getEmail(), user.getAvatarUrl());
    }
}
```

- **내부 식별자를 응답에 담지 않습니다.** 화면에 필요 없는 값은 빼고 내보냅니다.
- 외부 API 응답을 매핑하는 record에는 `@JsonIgnoreProperties(ignoreUnknown = true)` 를 붙입니다. 상대가 필드를 하나 추가하는 날 터지지 않게 합니다.

## 6. 요청 검증

형식 검증은 **컨트롤러 입구**에서, 업무 규칙 검증은 **서비스**에서 합니다.

의존성에 `spring-boot-starter-validation` 이 필요합니다.

```java
public record CoverLetterCreateRequest(
        @NotBlank(message = "공고를 선택해 주세요.") String postingId,
        @NotBlank @Size(max = 2000) String answer) {}
```

```java
@PostMapping("/api/cover-letters")
public ResponseEntity<CoverLetterResponse> create(@Valid @RequestBody CoverLetterCreateRequest request) {
    ...
}
```

- 요청 DTO의 필드에 `@NotBlank` · `@NotNull` · `@Size` · `@Positive` 등을 붙이고, 컨트롤러 파라미터에 `@Valid` 를 붙입니다.
- 검증에 걸리면 Spring이 `MethodArgumentNotValidException` 을 던지고, 핸들러가 **400** 으로 바꿉니다 (7절).
- **서비스에서 같은 형식 검사를 또 하지 않습니다.** 단, "우리 워크스페이스 계정인가",
  "남의 자기소개서를 건드리는가" 같은 **업무·보안 규칙은 반드시 서비스에서** 확인합니다
  (`SlackLoginService.requireAllowedWorkspace` 가 그 예입니다). 컨트롤러 검증은 우회될 수 있습니다.

## 7. 예외 처리

- 예외 클래스는 **도메인별로** 만듭니다. `RuntimeException` 을 상속하고, 상태 코드를 예외가 들고 있게 합니다.
- 예외 생성은 `static` 팩토리 메서드로 합니다 — `AuthException.loginRequired()`.
- **`@RestControllerAdvice` 는 프로젝트에 하나뿐입니다.** `global/exception/GlobalExceptionHandler` 에 두고
  모든 예외를 여기서 받습니다. 도메인마다 만들면 팀원 수만큼 에러 응답 형식이 갈립니다.
- 도메인 예외는 `global/exception/ApiException` 을 상속해 상태 코드와 명세(docs/api-spec-v6.md 9절)의 `code` 를 들고 있게 합니다.
- 응답 형식은 `{"code": "...", "message": "..."}` 하나로 통일합니다. 인증(`AuthException`)도 같습니다.
- **응답 메시지에 내부 사정을 담지 않습니다.** 원인·스택트레이스·내부 ID는 로그에만 남깁니다.

```java
throw AuthException.workspaceNotAllowed();
// 응답: 403 {"code": "WORKSPACE_NOT_ALLOWED", "message": "허용되지 않은 Slack 워크스페이스입니다."}
```

로그인 확인은 컨트롤러가 아니라 `global/security/SessionAuthInterceptor` 가 `/api/**` 앞에서 합니다(`/api/auth/**` · `/internal/**` 제외).
컨트롤러는 사용자 객체가 필요할 때 `currentUser.require(request)` 를 그대로 부릅니다 — 같은 요청 안에서는 DB 를 다시 읽지 않습니다.

핸들러가 받아야 할 것은 셋입니다.

| 대상 | 응답 |
| --- | --- |
| 도메인 예외 (`AuthException` 등) | 예외가 들고 있는 상태 코드 |
| `MethodArgumentNotValidException` | 400 |
| 그 밖의 `Exception` | 500 + 고정 메시지. 반드시 `log.error` 로 남깁니다 |

| 상황 | 상태 코드 |
| --- | --- |
| 요청 값이 잘못됨 | 400 |
| 로그인이 필요함 | 401 |
| 권한이 없음 | 403 |
| 대상이 없음 | 404 |
| 서버 오류 | 500 |

## 8. 설정과 비밀값

- 설정은 `application.yml` 에 둡니다. `application.properties` 는 만들지 않습니다 (Spring Initializr가 만들었으면 지웁니다).
- **실제 비밀값은 저장소에 넣지 않습니다.** `application.yml` 에는 `${SLACK_CLIENT_SECRET:}` 처럼 환경변수 참조만 둡니다.
- 비밀값은 루트 `.env`에 넣습니다. 이 파일은 `.gitignore`되어 있습니다.
- 설정 묶음은 `record` + `@ConfigurationProperties` 로 받습니다 (`AuthProperties`). 메인 클래스에 `@ConfigurationPropertiesScan` 이 있어야 동작합니다.
- 값을 `@Value` 로 여기저기 흩어 받지 않습니다.

### 스키마는 Flyway가 만듭니다

`ddl-auto: validate`로 두어 Hibernate가 공용 DB를 임의로 변경하지 않게 합니다.
스키마 변경은 `src/main/resources/db/migration` 아래의 Flyway 마이그레이션으로만 적용합니다.
이미 적용된 마이그레이션은 수정하지 말고 다음 버전 파일을 추가합니다.


### CORS

프론트가 다른 포트에서 뜨면 필요합니다. 설정 클래스는 `global/config` 에 하나만 둡니다.

- **`allowedOrigins("*")` 를 쓸 수 없습니다.** 로그인 상태를 세션 쿠키로 유지하기 때문에
  `allowCredentials(true)` 가 필요한데, 브라우저는 `*` 와 이 조합을 거부합니다.
  허용할 주소를 명시적으로 적습니다.
- 허용 주소는 코드에 박지 말고 `application.yml` 로 뺍니다. 로컬과 배포 주소가 다릅니다.

## 9. 로깅

- SLF4J를 씁니다. `System.out.println` 을 쓰지 않습니다.

```java
private static final Logger log = LoggerFactory.getLogger(SlackLoginService.class);
```

- 문자열을 더하지 말고 `{}` 자리표시자를 씁니다 — `log.info("... teamId={}", teamId);`
- **토큰·시크릿·비밀번호를 로그에 남기지 않습니다.**
- 레벨: `error`(사람이 손봐야 함) / `warn`(외부 호출 실패 등 의심스러움) / `info`(주요 흐름) / `debug`(개발 중 확인용).

## 10. 주석

- Javadoc은 **한국어**로 씁니다. 클래스와 공개 메서드 중 설명이 필요한 것에 답니다.
- **무엇을 하는지가 아니라 왜 그런지를 씁니다.** 나중에 누가 "이거 왜 이렇게 했지?" 하고 되돌릴 만한 결정에는 반드시 이유를 남깁니다.
- 중요한 제약은 `<b>...</b>` 로 강조합니다.
- 파라미터 설명이 필요하면 `@param`, 던지는 예외는 `@throws` 를 씁니다.

```java
/**
 * 워크스페이스 제한. <b>이 서비스를 거치지 않고 로그인되는 경로는 없다.</b>
 *
 * <p>비어 있는 값도 거부한다. null 을 통과시키면 검사가 통째로 무력해진다.
 */
```

- 지워진 코드를 주석으로 남겨 두지 않습니다. Git이 기억합니다.

## 11. 테스트

- JUnit 5 + AssertJ + Mockito를 씁니다.
- 위치는 `src/test/java` 에 운영 코드와 같은 패키지로 둡니다. 클래스 이름은 `~Test` 입니다.
- **테스트 메서드 이름은 한국어로, 무엇이 보장되는지를 문장으로 씁니다.**

```java
@Test
void 다른_워크스페이스_계정은_거부된다() {
    ...
}
```

- 단언은 `assertThat(...)` / `assertThatThrownBy(...)` 를 씁니다.
- **`@SpringBootTest` 를 기본으로 쓰지 않습니다.** 스프링 컨텍스트를 띄우면 테스트 하나에 몇 초씩 걸립니다.
  의존성을 `mock(...)` 으로 만들어 생성자로 넣고 검증하는 게 기본입니다 (`SlackLoginServiceTest` 참고).
  컨텍스트가 꼭 필요할 때만 `@SpringBootTest`, 웹 계층만 필요하면 `@WebMvcTest`, JPA만 필요하면 `@DataJpaTest` 를 씁니다.
- 시간이 없으면 전부 짤 필요는 없습니다. **깨지면 서비스가 뚫리거나 데이터가 망가지는 것부터** 짭니다.

## 12. PR 올리기 전 확인

- [ ] 빌드와 테스트가 통과합니다 (`./gradlew build` — 프로젝트 세팅이 끝난 뒤부터)
- [ ] 포맷을 적용했습니다 (들여쓰기 4칸 / 120자 / 와일드카드 import 없음)
- [ ] 비밀값·API Key·`.env` 가 포함되지 않았습니다
- [ ] 컨트롤러가 엔티티를 그대로 반환하지 않습니다
- [ ] 새로 만든 연관관계에 `fetch = FetchType.LAZY` 가 붙어 있습니다
- [ ] 요청 DTO에 검증 애너테이션과 `@Valid` 가 붙어 있습니다
- [ ] 새 API는 `docs/` 의 API 문서에 반영했습니다 (문서가 만들어진 뒤부터)
- [ ] 남겨 둔 `System.out.println` · 디버그 코드 · 주석 처리된 코드가 없습니다
