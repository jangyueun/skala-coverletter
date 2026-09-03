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

  it('남은 시간을 올림한다 — 19일 6시간 남았으면 D-20', () => {
    expect(dday('2026-09-21 18:00', at('2026-09-02 12:00'))).toBe(20)   // 19.25 → 20
    expect(dday('2026-09-21 18:00', at('2026-09-02 18:00'))).toBe(19)   // 정확히 19.0
  })
})
