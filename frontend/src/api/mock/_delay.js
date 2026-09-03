/* 목이 진짜처럼 굴게 하는 두 스위치.
 *
 * 즉시 반환하는 목은 로딩 코드를 한 번도 실행하지 않은 채 백엔드를 맞는다.
 * 그래서 기본으로 300~800ms 기다린다.
 *
 *   VITE_API_MOCK_DELAY=3000        로딩 상태를 오래 보고 싶을 때
 *   VITE_API_MOCK_FAIL=postings,ai  그 리소스만 500 을 던져 실패 화면을 볼 때 */

import { ApiError } from '../client.js'

const FIXED = Number(import.meta.env.VITE_API_MOCK_DELAY)
const FAIL = new Set((import.meta.env.VITE_API_MOCK_FAIL || '').split(',').map(s => s.trim()).filter(Boolean))

export async function delay(resource) {
  const ms = Number.isFinite(FIXED) ? FIXED : 300 + Math.random() * 500
  await new Promise(r => setTimeout(r, ms))
  if (FAIL.has(resource)) throw new ApiError(500, `(mock) ${resource} 실패 — VITE_API_MOCK_FAIL`)
}

/** 목은 자기 배열을 고친다. 원본 DATA 를 건드리면 새로고침 전까지 되돌릴 수 없다. */
export const clone = x => JSON.parse(JSON.stringify(x))
