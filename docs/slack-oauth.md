# Slack 로그인

우리 워크스페이스(`T089ENT4A2D`) 멤버만 로그인된다. 다른 워크스페이스 계정은 403 으로 막힌다.

---

## 0. 프로젝트를 만든 다음 해야 할 것

이 폴더에는 **auth 코드만** 있다. Spring Boot 프로젝트 자체(`build.gradle`, `gradlew`,
메인 클래스)는 따로 만들어 `backend/` 에 넣는다. 넣고 나서 아래 셋을 반드시 한다.

### ① 의존성

```groovy
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
runtimeOnly   'org.postgresql:postgresql'
testImplementation 'org.springframework.boot:spring-boot-starter-test'
```

> `spring-boot-starter-restclient` 는 **필요 없다.** Boot 4 가 `RestClient.Builder`
> 자동 설정을 별도 모듈로 뺐기 때문에, Builder 를 주입받게 짜면 그 의존성이 없는 순간
> 기동이 실패한다. 그래서 `SlackOAuthClient` 는 `RestClient.create()` 로 직접 만든다.

### ② 메인 클래스에 `@ConfigurationPropertiesScan`

**이걸 빠뜨리면 `AuthProperties` 빈이 안 만들어져서 기동에서 바로 죽는다.**

```java
@SpringBootApplication
@ConfigurationPropertiesScan          // ← 이 줄
public class CareerfitApplication {
    public static void main(String[] args) {
        SpringApplication.run(CareerfitApplication.class, args);
    }
}
```

### ③ `application.properties` 삭제

Initializr 가 만들어 준다. 우리는 `application.yml` 을 쓰니 둘 다 두면 헷갈린다.

---

## 1. Slack App 설정 (사람이 직접)

<https://api.slack.com/apps> → Create New App → From scratch → 워크스페이스 선택.

| 항목 | 값 |
|---|---|
| **OpenID Connect** → Redirect URLs | `http://localhost:8080/api/auth/slack/callback` (로컬)<br>배포 주소가 생기면 `https://.../api/auth/slack/callback` 추가 |
| **User Token Scopes** | `openid`, `profile`, `email` |

봇 토큰이나 `chat:write` 는 **요청하지 않는다.** 사람을 로그인시키는 것뿐이라 받을
이유가 없고, 받아 두면 유출됐을 때 피해만 커진다.

`Basic Information` 에서 **Client ID** 와 **Client Secret** 을 복사한다.

---

## 2. 로컬 실행

```bash
cp .env.example .env
```

`.env`에 Supabase DB 접속 정보와 Slack Client ID / Secret을 채운다.
이 파일은 백엔드 로컬 개발 전용이고 `.gitignore`되어 있으므로 커밋하지 않는다.
프론트엔드 환경변수는 `frontend/.env.example`을 사용한다.

Spring Boot가 `.env`를 properties 형식으로 직접 읽으므로 `source`하지 않는다.
값에 쉘용 따옴표를 붙이지 않아야 Docker·IntelliJ와도 같은 파일을 쓸 수 있다.

```bash
cd backend
./gradlew bootRun
```

브라우저에서 <http://localhost:8080/api/auth/slack/start> 로 들어가면 Slack 동의 화면이 뜬다.

---

## 3. 엔드포인트

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| GET | `/api/auth/slack/start?returnTo=/experiences` | Slack 동의 화면으로 302. `returnTo` 는 로그인 후 돌아올 **경로**(호스트 붙은 값은 버린다) |
| GET | `/api/auth/slack/callback?code=..&state=..` | Slack 이 여기로 돌려보낸다. 세션 발급 후 `returnTo` 로 302 |
| GET | `/api/auth/me` | 로그인 상태 확인. 로그인 안 됐으면 **200 + `null`** |
| POST | `/api/auth/logout` | 서버 세션 폐기. 204 |

### 프론트에서

```js
// 로그인 버튼 — fetch 가 아니라 페이지 이동이어야 한다. OAuth 는 리다이렉트로 돈다.
location.href = `/api/auth/slack/start?returnTo=${encodeURIComponent(location.pathname)}`

// 앱 켤 때 한 번
const me = await fetch('/api/auth/me', { credentials: 'include' }).then(r => r.json())
if (me) { /* 로그인됨 */ }

await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
```

프론트를 다른 포트(Vite 5173 등)에서 띄우면 **프록시를 걸어 같은 오리진으로 만든다.**
CORS + 쿠키 조합은 `SameSite` 때문에 잘 물린다.

```js
// vite.config.js
server: { proxy: { '/api': 'http://localhost:8080' } }
```

---

## 4. 워크스페이스 제한이 걸리는 곳

`SlackLoginService.requireAllowedWorkspace()` 한 곳이다. 우회 경로가 없는 이유:

- `team_id` 는 **우리 서버가 액세스 토큰으로 Slack 에 직접 물어본 값**이다
  (`openid.connect.userInfo` 의 `https://slack.com/team_id` 클레임).
  클라이언트가 보낸 값이 아니므로 위조할 수 없다.
- 인가 URL 의 `&team=` 파라미터는 로그인 화면을 미리 골라 주는 **편의**일 뿐이다.
  보안 수단이 아니라서, 사용자가 그 파라미터를 지워도 콜백 검증은 그대로 걸린다.
- `team_id` 가 비어 있어도 거부한다. null 을 통과시키면 Slack 응답 형식이 바뀌는 날
  검사가 통째로 무력해진다.
- 사용자 생성은 이 검사 **뒤에** 있다. 거부된 계정은 DB 에 남지 않는다.

---

## 5. 이 코드가 막고 있는 것

| 공격 | 방어 | 위치 |
|---|---|---|
| 로그인 CSRF | `state` 난수 + 상수 시간 비교 | `SlackAuthController.requireValidState` |
| 인가 코드 가로채기 | PKCE (S256) | `challengeOf` / `code_verifier` |
| Host 헤더 위조로 코드 탈취 | `redirect_uri` 를 설정값에서만 읽는다 | `AuthProperties.redirectUri` |
| 열린 리다이렉트 | `//` 로 시작하는 `returnTo` 거부 | `safeReturnPath` |
| 세션 고정 | 로그인 시 기존 세션 폐기 후 재발급 | `callback()` |
| state 재사용 | 읽는 즉시 쿠키 삭제 | `clearHandshakeCookies` |
| 쿠키 탈취(JS) | `HttpOnly` | 핸드셰이크 쿠키 · 세션 쿠키 |
| 정보 노출 | 실패 사유는 로그에만, 응답은 한 문장 | `AuthException` |

### 아직 안 한 것

- **CSRF 토큰** — 로그인 자체는 `state` 로 막혀 있지만, 로그인 후의 상태 변경 API
  (POST/PUT/DELETE)에는 별도 방어가 없다. `SameSite=Lax` 가 교차 사이트 POST 를 막아
  주긴 하지만 그것만 믿을 건 아니다. 기능 API 를 붙일 때 같이 정하자.
- **인가 필터** — 지금은 `CurrentUser.require()` 를 컨트롤러에서 직접 부르는 방식이다.
  보호할 API 가 늘어나면 필터나 인터셉터로 옮기는 게 낫다.

---

## 6. 함정

**`SameSite` 를 `Strict` 로 바꾸지 마라.** Slack 콜백은 외부 도메인에서 우리 서버로
오는 최상위 이동이다. `Strict` 쿠키는 그 요청에 실리지 않아서 `state` 대조가 **항상**
실패한다. 증상은 "로그인 눌렀는데 계속 실패" 이고 원인을 찾는 데 한참 걸린다.

**로컬에서 `Secure` 쿠키는 저장되지 않는다.** `http://localhost` 로 개발하면
`cookie-secure: false` 여야 한다. 로컬 전용 `.env`에서만
`SESSION_COOKIE_SECURE=false`로 덮어쓰고, 배포 환경은 기본값 `true`를 유지한다.

**`redirect_uri` 는 문자 하나까지 같아야 한다.** Slack App 설정, `application.yml`,
토큰 교환 요청 세 곳이 전부 같아야 한다. 끝의 `/` 하나 차이로도 거부된다.

**Slack 은 실패도 HTTP 200 으로 준다.** 본문 `ok: false` 를 확인하지 않으면 실패를
성공으로 읽는다. `SlackApiResponses` 가 `ok` 를 담고 있는 이유다.
