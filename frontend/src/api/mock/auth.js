/* 목 인증. 백엔드 없이 띄울 때(VITE_API_MOCK=1)만 쓴다.
 * 기본이 로그인 상태인 이유 — 이 앱의 모든 화면이 "내 경험" 위에 서 있어서
 * 로그아웃으로 시작하면 보여 줄 것이 없다. */
import { delay } from './_delay.js'

let signedIn = true
const USER = { id: 1, displayName: '김지호', email: 'simonjiho@gmail.com', avatarUrl: null }

export async function me()      { await delay('auth'); return signedIn ? { ...USER } : null }
export async function signIn()  { await delay('auth'); signedIn = true;  return { ...USER } }
export async function signOut() { await delay('auth'); signedIn = false; return null }
