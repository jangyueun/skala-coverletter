/* 인증 — 백엔드에 실제로 있는 유일한 API. 계약은 docs/slack-oauth.md.
 *
 *   GET  /api/auth/me            200 + {id, displayName, email, avatarUrl} | null
 *   GET  /api/auth/slack/start   302 → Slack (브라우저가 통째로 이동한다)
 *   POST /api/auth/logout        204 */

import { client } from '../client.js'

/** 로그인 안 됐으면 null. 401 이 아니라 200 + null 이라 catch 가 필요 없다. */
export const me = () => client.get('/auth/me')

/**
 * 로그인은 fetch 가 아니라 페이지 이동이다. Slack 이 302 로 우리 콜백에 돌려주고,
 * 콜백이 세션을 만든 뒤 returnTo 로 다시 보낸다. 돌아오면 me() 가 사용자를 준다.
 */
export function signIn(returnTo = location.pathname) {
  location.href = `/api/auth/slack/start?returnTo=${encodeURIComponent(returnTo)}`
  return new Promise(() => {})   // 페이지가 떠나므로 resolve 되지 않는다
}

export const signOut = () => client.post('/auth/logout')
