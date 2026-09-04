import { describe, it, expect } from 'vitest'
import { deadlineAt, dday, isClosed, deadlineLabel } from '@/domain/deadline.js'

/* 실제로 터진 것 — 마감 시각이 생기자 dday 로 마감을 판정하던 코드가 -0 에 속았다. */
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

  it('오프셋이 붙은 ISO — v6 가 주는 형식 — 는 그 시간대의 시각으로 읽는다', () => {
    /* '+09:00' 을 버리고 로컬로 읽으면 다른 시간대에서 마감이 몇 시간 어긋난다. */
    expect(deadlineAt('2026-09-12T18:00:00+09:00').getTime()).toBe(Date.UTC(2026, 8, 12, 9, 0, 0))
    const justAfter = new Date(Date.UTC(2026, 8, 12, 9, 0, 1))
    expect(isClosed('2026-09-12T18:00:00+09:00', justAfter)).toBe(true)
    expect(isClosed('2026-09-12T18:00:00+09:00', new Date(Date.UTC(2026, 8, 12, 8, 59)))).toBe(false)
  })

  it('deadlineLabel 은 보는 사람의 시간대로 YYYY-MM-DD HH:mm 을 만든다', () => {
    const label = deadlineLabel('2026-09-12T18:00:00+09:00')
    expect(label).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
    // 같은 순간을 다른 표기로 주면 같은 라벨이어야 한다 — 오프셋을 무시하면 여기서 갈린다
    expect(deadlineLabel('2026-09-12T09:00:00Z')).toBe(label)
    expect(deadlineLabel('2026-09-12T18:00:00+09:00')).not.toContain('+')
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
