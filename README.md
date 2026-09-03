# Career Lab

내 경험(STAR)을 쌓아 두고, 채용 공고의 요구 역량과 맞춰 보고, 자소서를 쓰는 곳.
`frontend/`(Vue) · `backend/`(Spring Boot) · `ai/`(FastAPI, 지금은 Mock) 세 서버다.

## 바로 띄우기

```bash
docker compose -f compose.yaml -f compose.localdb.yaml up --build
```

<http://localhost:5173> 으로 들어간다. Supabase 를 쓰려면 `cp .env.example .env` 뒤 값을 채우고 `docker compose up --build`.
버전 표준·로컬 개발·함정은 [docs/dev-environment.md](docs/dev-environment.md).

## 문서

| 문서 | 내용 |
|---|---|
| [docs/dev-environment.md](docs/dev-environment.md) | 버전 표준(Java 21 · Node 24 · Python 3.13 · Postgres 17) · Docker · 로컬 개발 |
| [docs/api-spec-v6.md](docs/api-spec-v6.md) | API 명세. DTO 모양과 오류 코드는 여기가 기준 |
| [docs/erd-v6.dbml](docs/erd-v6.dbml) | ERD (dbdiagram.io) |
| [docs/slack-oauth.md](docs/slack-oauth.md) | 로그인 계약 · Slack App 설정 · 무엇을 막고 있나 |
| [docs/frontend-architecture.md](docs/frontend-architecture.md) | 프론트 계층 · 목/실제 API 전환 |
| [docs/backend-convention.md](docs/backend-convention.md) · [docs/git-convention.md](docs/git-convention.md) | 코드 · 브랜치 · PR 규칙 |
