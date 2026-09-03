# 프런트 아키텍처

**기준은 `frontend/src` 다.** 이 문서는 "지금 이렇게 생겼다" 가 아니라 "이렇게 재구성한다" 를 적는다.
아래 계층대로 코드를 옮기고, 옮긴 뒤에는 이 문서가 곧 현재 상태다.

같이 읽을 것 — [`ai-flow.md`](./ai-flow.md)(AI 파이프라인), [`slack-oauth.md`](./slack-oauth.md)(로그인 계약).

---

## 0. 한 장

```
                  화면이 보는 것            서버와 말하는 것         순수 계산
                  ─────────────            ─────────────────        ─────────
 views/     ──▶  stores/         ──▶      api/              ──▶   (백엔드 :8080)
 components/     상태 · loading · error    Promise 만 돌려줌
                       │                        │
                       └────────────────────────┼──▶  domain/
                                                │     matching · essay · deadline
                                                │     서버도 화면도 모름
                                          api/mock/
                                          같은 시그니처 · 지연 흉내
                                          VITE_API_MOCK=1 이면 이쪽
```

세 줄로 —

1. **화면은 스토어만 본다.** 컴포넌트가 `fetch` 를 부르거나 목 데이터를 import 하지 않는다.
2. **스토어는 `api/` 만 부른다.** 전부 `await` 한다. 기다리는 동안 `loading`, 실패하면 `error` 를 든다.
3. **계산은 `domain/` 에 있다.** 매칭률·자소서 진행·마감 판정은 서버도 화면도 모르는 순수 함수다. 테스트는 여기가 제일 싸다.

---

## 1. 왜 이 구조인가 — 우리 제약 세 개

| 제약 | 그래서 |
|---|---|
| **백엔드가 아직 없다.** 지금 있는 건 인증(`/api/auth/*`)뿐이고 공고·경험·답변 API 는 팀원이 만드는 중이다. | `api/` 를 **시그니처만 먼저** 정하고 `api/mock/` 이 그 시그니처를 채운다. 실제 API 가 오면 `api/real/` 파일 하나씩 갈아 끼운다. 화면·스토어는 안 고친다. |
| **5명이 3일.** 프런트·백엔드가 동시에 움직이고, 서로 기다릴 시간이 없다. | 목이 **지연을 흉내낸다**(300~800ms). 로딩 상태를 백엔드 없이도 개발 모드에서 본다. 백엔드가 붙는 날 "로딩이 안 보이던 화면" 이 갑자기 깜빡이는 일이 없다. |
| **빌드 전에 확인하고 싶다.** 데모 직전에 깨진 걸 브라우저에서 발견하면 늦다. | `npm run build` 가 **테스트를 먼저 돌리고** 통과해야 빌드한다. `domain/` 은 DOM 없이, `stores/` 는 `api/mock` 으로, 서버 없이 다 돈다. |

---

## 2. 계층

### `domain/` — 순수 계산

지금의 `lib/matching.js` `lib/lint.js` 가 여기로 온다. 규칙 하나 — **import 가 없다.** `DATA` 도, 스토어도, Vue 도.

```
domain/
  matching.js    computeMatch · topGap · usedIn · SCORE · STR
  essay.js       essayProgress · lengthState · ESSAY_STATE
  deadline.js    deadlineAt · dday · isClosed
  competency.js  CATEGORIES · groupByCategory · catShort
```

지금 `matching.js` 는 `DATA.competencies` 를 기본 인자로 잡는다. 그게 오늘 하루에 두 번 사고를 냈다 —
`essayProgress()` 가 스토어 사본이 아니라 원본을 읽어 저장해도 화면이 안 움직였고,
`posting 9` 의 옛 id 가 `byId()` 를 조용히 통과해 엉뚱한 역량을 그렸다.
**기본 인자를 없앤다.** 모든 함수가 필요한 데이터를 인자로 받는다. 안 받으면 터진다 — 그게 맞다.

### `api/` — 서버와 말하는 유일한 층

함수마다 **Promise 를 돌려준다.** 반환 모양은 백엔드 DTO 를 그대로 따른다(변환은 스토어 몫).

```
api/
  client.js         fetch 래퍼. base '/api', credentials:'include', 에러 정규화
  index.js          VITE_API_MOCK=0 이면 real, 아니면 mock — 여기서만 갈라진다
  real/
    auth.js         me() · signIn() · signOut()          ← 실제 백엔드 있음
    ai.js           extract(text) · draft(questionId, usedExperienceIds)   ← dev 는 vite 플러그인이 서빙
    (postings·experiences·answers 는 백엔드가 생기면 여기 추가)
  mock/
    _delay.js       지연 · VITE_API_MOCK_FAIL
    data.js         옛 mockData.js
    auth.js · postings.js · experiences.js · answers.js · ai.js   같은 시그니처
```

`client.js` 가 하는 일은 셋뿐이다 —

```js
// 세션 쿠키는 HttpOnly 라 JS 가 못 읽는다. 대신 fetch 가 실어 보내게 한다.
// dev 는 vite 프록시가 /api 를 :8080 으로 넘기므로 같은 오리진이다.
credentials: 'include'

// 백엔드가 401 대신 200 + null 을 주는 곳(/auth/me)이 있다 — 그대로 통과시킨다.
// 그 밖의 4xx/5xx 는 ApiError 로 던진다. 스토어가 error 에 담는다.

// JSON 이 아닌 응답(204, 302)을 다룬다.
```

**목은 진짜처럼 군다.** 지연을 넣고, 저장은 자기 배열에 쓰고, 다시 읽으면 바뀐 게 보인다.
목이 즉시 반환하면 로딩 상태 코드는 한 번도 실행되지 않은 채 백엔드를 맞는다.

### `stores/` — 상태 + 비동기

지금 `careerStore` 하나가 공고·경험·문항·버퍼·즐겨찾기를 다 든다. 백엔드가 붙으면 각각이 다른 시점에 다른 API 로 갱신되므로 **관심사별로 나눈다.**

```
stores/
  auth.js         user · signedIn · loaded · load() · signIn() · signOut()
  postings.js     list · competencies(사전) · live · load()
  experiences.js  list · candidates · taggedCompetencyIds · load() · create() · update()
  answers.js      applications · questions · drafts(버퍼) · savedAt · saving · load() · save(questionId)
  ui.js           sort · bookmarks — 서버가 모르는 화면 상태
  derived.js      state 없음. 넷을 읽어 cards · myLists · topGap · matchFor · essayFor · usedIn
```

`derived.js` 를 따로 둔 이유 — `cards` 는 공고+경험+답변+즐겨찾기를 다 읽는데 넷 중 어느 하나의 것도 아니다.
공고 스토어에 두면 공고 스토어가 답변 스토어를 알게 되고, 백엔드가 붙어 갱신 시점이 갈라질 때 그 결합이 발목을 잡는다.

스토어마다 같은 꼴을 지킨다 —

```js
state: () => ({ list: [], loading: false, error: null, loadedAt: null })

async load() {
  if (this.loading) return              // 중복 호출 흡수
  this.loading = true; this.error = null
  try   { this.list = await api.postings.list(); this.loadedAt = Date.now() }
  catch (e) { this.error = e }
  finally { this.loading = false }
}
```

**파생값은 `derived.js` 의 getter 다.** 스토어 넷을 읽어 `domain/` 함수를 부른다. 계산은 스토어에 없다.
`derived.ready` 가 넷이 다 왔는지를 말한다 — 화면은 그걸 보고 Skeleton 을 그린다.

**버퍼(자소서 편집분)는 지금처럼 스토어에 둔다.** 저장은 `await api.answers.save()` 하고, 성공하면 커밋본을 갈아 끼우고 버퍼를 지운다. 실패하면 버퍼는 남는다 — 쓴 글은 안 사라진다.

### `components/` · `views/` — 그대로

구조는 안 바뀐다. 바뀌는 건 **로딩·실패 상태를 그린다** 는 것 하나다.

```vue
<Skeleton v-if="store.loading" />
<ErrorNote v-else-if="store.error" :error="store.error" @retry="store.load()" />
<template v-else> …지금 화면… </template>
```

`Skeleton` `ErrorNote` 두 컴포넌트를 `components/state/` 에 둔다. 화면마다 따로 그리면 다섯 가지 로딩이 생긴다.

---

## 3. 비동기 규칙

- **스토어 액션은 전부 `async`.** 목이라도 `await` 한다. 나중에 `await` 를 넣는 게 아니라, 지금 넣고 목이 즉시 resolve 하지 않게 한다.
- **화면은 `await` 하지 않는다.** `onMounted(() => store.load())` 로 부르기만 하고, 결과는 스토어의 `loading`/`list` 로 받는다. 컴포넌트에 `try/catch` 가 있으면 잘못 짠 것이다.
- **경합은 스토어가 막는다.** `if (this.loading) return`. 탭을 빨리 두 번 눌러도 요청은 하나다.
- **AI 초안처럼 오래 걸리는 것**은 대상 id 를 시작할 때 붙잡는다. 오늘 고친 버그 — `await` 뒤에 `q.value` 를 다시 읽어 다른 문항에 꽂혔다. `const target = q.value` 를 `await` 앞에 둔다.
- **`beforeunload` 는 버퍼가 있을 때만.** 라우터 이동은 막지 않는다 — 버퍼는 스토어에 있어 안 사라진다.

---

## 4. 개발 모드

| 하고 싶은 것 | 하는 법 |
|---|---|
| 백엔드 없이 화면 개발 | `npm run dev` — **기본이 목**이다. 지연 300~800ms |
| 백엔드 붙여서 확인 | `VITE_API_MOCK=0 npm run dev` — 인증·AI 가 real 로. vite 프록시가 `/api` → `:8080`. 같은 오리진이라 CORS·쿠키 문제 없음 |
| 로딩 상태를 오래 보고 싶다 | `VITE_API_MOCK_DELAY=3000` |
| 실패 화면을 보고 싶다 | `VITE_API_MOCK_FAIL=postings` — 그 API 만 500 을 준다 |
| AI 추출·초안 | `.env` 에 `ANTHROPIC_API_KEY` — vite 플러그인이 `/api/ai/*` 를 dev 에서 서빙. 키 없으면 503 + 이유 |
| 같은 Wi-Fi 팀원에게 보여주기 | `host: true` 라 `npm run dev` 가 찍는 Network 주소 |

`VITE_` 접두사는 값이 빌드 산출물에 박히므로 **스위치에만** 쓴다. 키는 절대 안 된다(`.env.example` 참조).

---

## 5. 테스트

```
npm test          vitest run — 한 번 돌고 끝
npm run test:w    vitest      — 파일 저장마다
npm run build     vitest run && vite build — 테스트 통과해야 빌드
```

무엇을 어디서 —

| 층 | 테스트 | 환경 | 예 |
|---|---|---|---|
| `domain/` | 입력 → 출력. DOM 없음 | node | 가중치 0.9 인 역량이 0.6 이면 매칭률이 얼마인가 · 오늘 18:00 마감을 20:00 에 보면 `isClosed` 인가 |
| `stores/` | 액션 → 상태. `api/mock` 주입 | node | `save()` 실패하면 버퍼가 남는가 · `load()` 두 번 불러도 요청 한 번인가 |
| `components/` | 렌더 → DOM. 스토어는 pinia testing | jsdom | 로그아웃이면 `SignInGate` 가 뜨는가 · 마감 공고에 D-day 가 안 뜨는가 |

**전부 쓰지 않는다.** 3일이다. 오늘 실제로 터진 것들부터 — `essayProgress` 가 주입된 questions 를 읽는가, `isClosed` 가 `-0` 에 안 속는가, 인테이크 등록 후 상태가 초기화되는가, `posting.required` 의 id 가 전부 사전에 있는가. 마지막 건 **데이터 검증 테스트**다 — 목 데이터가 사전과 어긋나면 빌드가 막힌다.

---

## 6. 백엔드가 붙는 날

| 파일 | 하는 일 |
|---|---|
| `api/real/postings.js` | `client.get('/postings')` — 5줄 |
| `api/index.js` | `postings: REAL ? realPostings : mockPostings` — 한 줄 |
| `stores/*` | **안 고침** — `await api.postings.list()` 는 그대로 |
| `views/`, `components/` | **안 고침** |
| `vite.config.js` | AI 를 Spring 이 서빙하기 시작하면 `aiDevServer()` 한 줄 삭제 |
| `.env` | `VITE_API_MOCK=0` (백엔드 있는 환경) |

응답 모양이 목과 다르면 **스토어에서 변환**한다. `api/` 는 DTO 를 그대로 넘기고 화면은 스토어 모양만 본다. 변환 지점이 한 곳이다.

---

## 7. 지금 코드에서 옮길 것

| 지금 | 어디로 | 비고 |
|---|---|---|
| `lib/matching.js` | `domain/matching.js` `domain/deadline.js` `domain/competency.js` | `DATA` 기본 인자 제거 |
| `lib/lint.js` | `domain/essay.js` | `essayProgress` 도 여기로 |
| `lib/mockData.js` | `api/mock/data.js` | 화면·스토어가 직접 import 하던 것 전부 끊음 |
| `stores/careerStore.js` | `stores/{postings,experiences,answers,ui,derived}.js` | 액션 전부 `async`. `assessLog`(렌더 안 됨) 삭제 |
| `stores/authStore.js` | `stores/auth.js` | `load()` 가 `/api/auth/me` 를 실제로 부름. `signIn()` 은 `location.href = '/api/auth/slack/start'` |
| (없음) | `api/{client,index}.js` · `api/real/{auth,ai}.js` · `api/mock/*` | 신설 |
| (없음) | `components/state/{Skeleton,ErrorNote}.vue` | 신설 |
| (없음) | `tests/` | `domain` 3파일 · `stores` 1파일 · 데이터 정합성 1파일 — 29개 |

옮기는 순서는 **아래에서 위로** — `domain` → `api` → `stores` → `views`. 각 단계에서 화면이 그대로 돌아야 한다.

**옮겼다.** 위 표대로 재구성했고 `npm run build` 가 테스트 29개를 먼저 돌린다.
