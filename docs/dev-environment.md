# 개발 환경 — 버전 표준과 Docker

팀원 컴마다 Java · Node · Python 버전이 달라서 "내 컴에선 되는데" 가 났다. 그래서 **버전을 문서로 정하고,
세 서버를 Docker 로 감쌌다.** 기준은 아래 표이고, 표의 출처는 각 `Dockerfile` 의 이미지 태그다 —
둘이 어긋나면 Dockerfile 이 맞다.

## 1. 기준 버전

| 무엇 | 버전 | 어디서 정해지나 | 로컬에 직접 깔 때 |
|---|---|---|---|
| Java | **21** (Temurin LTS) | `backend/Dockerfile` `eclipse-temurin:21` · `build.gradle` toolchain | `.tool-versions` |
| Gradle | 래퍼 **9.7.1** | `backend/gradle/wrapper/gradle-wrapper.properties` | 깔지 않는다. `./gradlew` 가 받는다 |
| Node | **24** (LTS) · npm 11 | `frontend/Dockerfile` `node:24-alpine` | `.nvmrc` · `.tool-versions` |
| Python | **3.13** | `ai/Dockerfile` `python:3.13-slim` (`pyproject` 최소 3.11) | `.tool-versions` |
| PostgreSQL | **17** | Supabase 프로젝트 · `compose.localdb.yaml` · Testcontainers `postgres:17-alpine` | 깔지 않는다. 컨테이너다 |
| nginx | 1.28 | `frontend/Dockerfile` | 로컬 dev 는 vite 가 대신한다 |
| Docker | Desktop 4.x (Compose v2) | — | <https://www.docker.com/products/docker-desktop/> |

`.tool-versions`(asdf · mise) 와 `.nvmrc`(nvm · fnm) 가 루트에 있다. 도구가 있으면 디렉터리에 들어갈 때 자동으로 맞춰진다.
Java 는 Temurin 배포판을 쓴다 — 다른 배포판이라도 21 이면 된다.

## 2. Docker 로 전부 띄우기

```bash
cp .env.example .env        # Supabase · Slack 값을 채운다. 로컬 DB 오버레이를 쓰면 Slack 값만 있어도 된다
docker compose up --build   # web(5173) · api(8080) · ai(8000)
```

브라우저는 <http://localhost:5173> 으로 들어간다. `/api` 는 nginx 가 `api` 컨테이너로 넘기므로 화면과 API 가 같은
오리진이고, 그래서 세션 쿠키가 그냥 붙는다(dev 의 vite 프록시와 같은 원리, `docs/slack-oauth.md` 3절).

### Supabase 없이 — 로컬 DB 오버레이

```bash
docker compose -f compose.yaml -f compose.localdb.yaml up --build
```

Postgres 17 컨테이너가 붙고 Flyway 가 V1~V6 마이그레이션과 시드(IT 공고 10건 · 역량 사전)를 올린다.
`.env` 가 아예 없어도 뜬다 — Slack 로그인만 안 된다. DB 를 처음부터 다시 만들려면 `down -v`.
호스트에서 붙을 땐 `localhost:15432`(postgres / postgres) — 5432 는 로컬 Postgres 와 부딪혀 피했다.

### 샘플 데이터 넣기 (로컬 DB)

빈 DB 에는 역량 사전 8개와 공고 10건뿐이라 화면이 썰렁하다. 프론트 목 데이터(`frontend/src/api/mock/data.js`)를 그대로 SQL 로 만든
스크립트가 `scripts/seed/` 에 있다 — 역량 사전 51개(+별칭)와 내 샘플 경험 6건. 여러 번 돌려도 안전하다.

```bash
docker compose -f compose.yaml -f compose.localdb.yaml exec -T db psql -U postgres < scripts/seed/competencies.sql
# Slack 로그인을 한 번 해서 users 에 내 행이 생긴 뒤, 그 이메일로
docker compose -f compose.yaml -f compose.localdb.yaml exec -T db psql -U postgres -v email=simonjiho@gmail.com < scripts/seed/my-experiences.sql
```

SQL 은 `node scripts/seed/generate.mjs` 가 만든다 — 손으로 고치지 말고 `data.js` 를 고친 뒤 다시 생성한다.
Flyway 마이그레이션에 두지 않는 이유는 팀 공용 Supabase 에 개인 경험이 들어가면 안 돼서다. Supabase 에 넣고 싶으면 같은 SQL 을
Supabase SQL Editor 에 붙여 넣으면 된다(사전은 팀이 합의한 뒤에).

### 자주 쓰는 것

| 하고 싶은 것 | 명령 |
|---|---|
| 한 서버만 다시 빌드 | `docker compose up --build api` |
| 로그 | `docker compose logs -f api` |
| 내리기 | `docker compose down` (로컬 DB 데이터까지: `down -v`) |
| 안에서 확인 | `curl localhost:5173/api/auth/me` → `null`(로그아웃) · `curl localhost:8000/health` |

### 함정

- **포트가 로컬 개발과 같다**(5173 · 8080 · 8000). `npm run dev` 나 `bootRun` 을 띄워 둔 채로 compose 를 올리면
  그 서비스만 포트 충돌로 못 뜬다. 둘 다 켜야 하면 호스트 포트만 바꾼다 — `WEB_PORT=15173 API_PORT=18080 docker compose up`.
  로컬 Postgres 가 있는 컴을 위해 db 는 처음부터 15432 다.
- **Slack Redirect URL 은 5173 이다** — `http://localhost:5173/api/auth/slack/callback`. 콜백이 nginx(또는 vite 프록시)를
  거쳐 api 로 가야 로그인 뒤 `returnTo` 가 웹 화면에 떨어진다. 8080 으로 등록하면 로그인은 되는데 Spring 의
  `/experiences` 로 떨어져 404 가 뜬다. `.env.example` 과 Slack App 설정을 같이 맞춘다.
- **`.env` 의 `\` 는 두 파서가 다르게 읽는다.** Spring 의 properties 파서는 `\\` 를 `\` 로 읽고, compose 의 env_file 은
  글자 그대로 넘긴다. DB 비밀번호에 `\` 가 있으면 Supabase 에서 비밀번호를 바꾸는 게 제일 싸다.
- **이미지 빌드에서는 백엔드 테스트가 안 돈다.** Testcontainers 가 Docker 데몬을 요구해 이미지 안에서 못 돈다.
  테스트 게이트는 로컬 `./gradlew test` 와 CI 다. 프론트는 반대로 `npm run build` 가 vitest 를 먼저 돌린다.
- **컨테이너의 웹은 언제나 실제 API 를 본다**(`VITE_API_MOCK=0` 이 빌드에 박힘). 목 화면은 `npm run dev` 로 본다.

## 3. 로컬 개발은 그대로

HMR 과 디버거는 컨테이너 밖이 빠르다. 평소 개발은 지금처럼 —

```bash
cd frontend && npm run dev                  # 목 모드. 백엔드 붙일 땐 VITE_API_MOCK=0 (frontend/.env.example)
cd backend && ./gradlew bootRun             # ../.env 필요
cd ai && uvicorn app.main:app --reload      # Mock, 키 불필요
```

Docker 는 **통합 확인 · 시연 · 새 팀원 온보딩** 용이다. "내 컴에서 안 뜬다" 가 나오면 먼저 `docker compose up --build` 로
띄워 본다 — 거기서 뜨면 로컬 도구 버전 문제고, 거기서도 안 뜨면 코드 문제다.

## 4. 컨테이너 안에서 각 서버가 어떻게 뜨나

| 서비스 | 이미지 | 안에서 하는 일 |
|---|---|---|
| `web` | `frontend/Dockerfile` | `npm ci` → `npm run build`(vitest + vite build) → nginx 가 `dist/` 서빙, `/api` 는 `api:8080` 으로 프록시(`frontend/nginx.conf`) |
| `api` | `backend/Dockerfile` | `./gradlew bootJar` → JRE 21 alpine 에서 `java -jar`. `SPRING_PROFILES_ACTIVE=docker` 라 `application.yml` 의 `../.env` 강제 import 가 꺼지고 값은 compose `env_file` 로 받는다. AI 서버 주소는 `http://ai:8000` |
| `ai` | `ai/Dockerfile` | `pip install .` → uvicorn. `ai/.env` 는 있으면 읽고 없으면 Mock 그대로 |
| `db` (오버레이) | `postgres:17-alpine` | 헬스체크 뒤에 api 가 뜬다. 데이터는 `pgdata` 볼륨 |

비밀값은 파일째 이미지에 들어가지 않는다 — `.dockerignore` 가 `.env*` 를 빼고, compose 가 실행 시점에 환경변수로 넣는다.
