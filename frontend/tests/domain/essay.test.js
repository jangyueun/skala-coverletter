import { describe, it, expect } from 'vitest'
import { essayProgress, usedIn, lengthState } from '@/domain/essay.js'

const apps = [{ id: 101, postingId: 9 }]
const posting = { id: 9 }

describe('essayProgress', () => {
  it('주입된 questions 를 읽는다 — 원본을 읽으면 저장해도 화면이 안 움직인다', () => {
    const before = [{ id: 1, applicationId: 101, draft: '' }, { id: 2, applicationId: 101, draft: '' }]
    const after  = [{ id: 1, applicationId: 101, draft: '썼다' }, { id: 2, applicationId: 101, draft: '썼다' }]
    expect(essayProgress(posting, apps, before).state).toBe('EMPTY')
    expect(essayProgress(posting, apps, after).state).toBe('DONE')
  })
  it('일부만 쓰면 WRITING', () => {
    const qs = [{ id: 1, applicationId: 101, draft: 'x' }, { id: 2, applicationId: 101, draft: '  ' }]
    expect(essayProgress(posting, apps, qs)).toMatchObject({ state: 'WRITING', done: 1, total: 2 })
  })
  it('지원서가 없으면 NO_APP', () => {
    expect(essayProgress({ id: 999 }, apps, []).state).toBe('NO_APP')
  })
})

describe('usedIn', () => {
  it('근거로 찍혔고 AND 본문이 있는 문항만 센다', () => {
    const qs = [
      { id: 1, applicationId: 101, draft: '있음', usedExperienceIds: [7] },
      { id: 2, applicationId: 101, draft: '',    usedExperienceIds: [7] },   // 본문 없음 → 안 셈
    ]
    expect(usedIn(7, apps, qs)).toEqual({ questions: 1, postings: 1 })
  })
})

describe('lengthState', () => {
  const q = { charLimit: 100 }
  it('80% 미만·초과는 bad, 사이는 ok, 빈칸은 idle', () => {
    expect(lengthState('', q).tone).toBe('idle')
    expect(lengthState('a'.repeat(50), q).tone).toBe('bad')
    expect(lengthState('a'.repeat(85), q).tone).toBe('ok')
    expect(lengthState('a'.repeat(101), q).tone).toBe('bad')
  })
})
