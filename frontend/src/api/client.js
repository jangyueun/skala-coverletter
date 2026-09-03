/* fetch 래퍼. 서버와 말하는 모든 코드가 이걸 거친다.
 *
 * 하는 일은 셋뿐이다 —
 * 1. base '/api' 를 붙인다. dev 는 vite 프록시가 :8080 으로 넘기고, 배포는 같은 오리진이다.
 * 2. credentials:'include'. 세션 쿠키가 HttpOnly 라 JS 가 못 읽는다 — fetch 가 실어 보내게 한다.
 * 3. 4xx/5xx 를 ApiError 로 던진다. 스토어가 error 에 담고 화면이 ErrorNote 로 그린다.
 *    오류 본문은 { code, message } 다(v6 9절) — code 는 body.code 로 읽는다.
 *    단 200 + null 은 그대로 통과 — /auth/me 가 로그아웃 상태를 그렇게 준다. */

export class ApiError extends Error {
  constructor(status, message, body) {
    super(message || `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

const BASE = '/api'

async function request(method, path, body) {
  /* multipart 는 FormData 를 그대로 넘긴다. Content-Type 을 우리가 쓰면 안 된다 —
     브라우저가 boundary 를 붙여 만들어야 하고, 우리가 덮어쓰면 서버가 본문을 못 자른다. */
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData
  const res = await fetch(BASE + path, {
    method,
    credentials: 'include',
    headers: body !== undefined && !isForm ? { 'Content-Type': 'application/json' } : {},
    body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
  })

  // 204·302 처럼 본문이 없는 응답
  const text = await res.text()
  const json = text ? safeJson(text) : null

  if (!res.ok) throw new ApiError(res.status, json?.message, json)
  return json
}

function safeJson(text) {
  try { return JSON.parse(text) } catch { return { raw: text } }
}

export const client = {
  get:   path         => request('GET', path),
  post:  (path, body) => request('POST', path, body),
  put:   (path, body) => request('PUT', path, body),
  patch: (path, body) => request('PATCH', path, body),
  del:   path         => request('DELETE', path),
}
