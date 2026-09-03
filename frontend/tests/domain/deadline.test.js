import { describe, it, expect } from 'vitest'
import { deadlineAt, dday, isClosed } from '@/domain/deadline.js'

/* 오늘 실제로 터진 것 — 마감 시각이 생기자 dday 로 마감을 판정하던 코드가 -0 에 속았다. */
describe('deadline', () => {
  const at = (s) => new Date(s.replace(' ', 'T') + ':00')

  it('시각까지 파싱한다', () => {
    expect(deadlineAt('2026-09-21 18:00').getHours()).toBe(18)
  })

  it('오늘 18시 마감을 저녁 8시에 보면 닫힌 것이다 — dday 가 -0 을 줘도', () => {
    const now = at('2026-09-21 20:00')
    expect(dday('2026-09-21 18:00', now)).toBe(-0)          // Math.ceil(-0.08) === -0
    expect(-0 >= 0).toBe(true)                              // 옛 판정이 왜 틀렸나
    expect(isClosed('2026-09-21 18:00', now)).toBe(true)     // 새 판정
  })

  it('오늘 18시 마감을 오전에 보면 D-1 이고 열려 있다', () => {
    const now = at('2026-09-21 09:00')
    expect(dday('2026-09-21 18:00', now)).toBe(1)
    expect(isClosed('2026-09-21 18:00', now)).toBe(false)
  })

  it('ISO 형식도 받는다 — 백엔드가 어떤 걸 줄지 모른다', () => {
    expect(deadlineAt('2026-09-21T18:00:00').getHours()).toBe(18)
    expect(isClosed('2026-09-21T18:00:00', at('2026-09-21 20:00'))).toBe(true)
  })

  it('읽을 수 없는 형식은 열려 있는 척하지 않고 터진다', () => {
    /* 옛 코드는 Invalid Date 를 만들었고, isClosed 의 `NaN < now` 가 false 라
       마감된 공고가 전부 살아 있는 것으로 보였다 — 조용한 fail-open. */
    expect(() => deadlineAt('2026년 9월 21일')).toThrow(/읽을 수 없/)
  })

  it('남은 시간을 올림한다 — 19일 6시간 남았으면 D-20', () => {
    expect(dday('2026-09-21 18:00', at('2026-09-02 12:00'))).toBe(20)   // 19.25 → 20
    expect(dday('2026-09-21 18:00', at('2026-09-02 18:00'))).toBe(19)   // 정확히 19.0
  })
})
