import { describe, it, expect } from 'vitest'
import { periodLabel, toMonth, fromMonth, periodValid, categoryLabel, EXPERIENCE_CATEGORIES } from '@/domain/experience.js'

/* v6 — 서버는 startDate·endDate(YYYY-MM-DD, NULL 허용)만 주고 표시 문자열은 프론트가 만든다. */
describe('periodLabel', () => {
  it('시작·종료가 있으면 "YYYY.MM – YYYY.MM"', () => {
    expect(periodLabel('2025-03-01', '2025-11-01')).toBe('2025.03 – 2025.11')
  })
  it('종료가 없으면 시작 월만 — 진행 중이거나 한 달짜리다', () => {
    expect(periodLabel('2026-08-01', null)).toBe('2026.08')
  })
  it('같은 달이면 한 번만', () => {
    expect(periodLabel('2026-08-01', '2026-08-01')).toBe('2026.08')
  })
  it('둘 다 없으면 "기간 미입력"', () => {
    expect(periodLabel(null, null)).toBe('기간 미입력')
    expect(periodLabel(undefined, undefined)).toBe('기간 미입력')
  })
  it('종료만 있어도 터지지 않는다 — 서버 CHECK 가 막지 않는 조합이다', () => {
    expect(periodLabel(null, '2025-11-01')).toBe('– 2025.11')
  })
})

describe('month 입력 ↔ 서버 날짜', () => {
  it('YYYY-MM-01 ↔ YYYY-MM 을 오간다. 빈 값은 null 로 보낸다', () => {
    expect(toMonth('2026-08-01')).toBe('2026-08')
    expect(toMonth(null)).toBe('')
    expect(fromMonth('2026-08')).toBe('2026-08-01')
    expect(fromMonth('')).toBeNull()
  })
  it('periodValid 는 서버 CHECK(end >= start)와 같다', () => {
    expect(periodValid('2025-03-01', '2025-11-01')).toBe(true)
    expect(periodValid('2025-11-01', '2025-03-01')).toBe(false)
    expect(periodValid('2025-03-01', '2025-03-01')).toBe(true)
    expect(periodValid(null, '2025-03-01')).toBe(true)
    expect(periodValid('2025-03-01', null)).toBe(true)
  })
})

describe('categoryLabel', () => {
  it('코드값 여섯 개에 한글 라벨이 다 있고, 모르는 코드는 그대로 돌려준다', () => {
    expect(EXPERIENCE_CATEGORIES).toHaveLength(6)
    expect(categoryLabel('TEAM_PROJECT')).toBe('팀 프로젝트')
    expect(categoryLabel('AWARD_CERTIFICATE')).toBe('수상·자격')
    expect(categoryLabel('WHATEVER')).toBe('WHATEVER')
  })
})
