/* fetch 래퍼. 서버와 말하는 모든 코드가 이걸 거친다.
 *
 * 하는 일은 셋뿐이다 —
 * 1. base '/api' 를 붙인다. dev 는 vite 프록시가 :8080 으로 넘기고, 배포는 같은 오리진이다.
 * 2. credentials:'include'. 세션 쿠키가 HttpOnly 라 JS 가 못 읽는다 — fetch 가 실어 보내게 한다.
 * 3. 4xx/5xx 를 ApiError 로 던진다. 스토어가 error 에 담고 화면이 ErrorNote 로 그린다.
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
  const res = await fetch(BASE + path, {
    method,
    credentials: 'include',
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : {},
    body: body !== undefined ? JSON.stringify(body) : undefined,
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
