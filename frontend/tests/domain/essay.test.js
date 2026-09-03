import { describe, it, expect } from 'vitest'
import { essayProgress, usedIn, lengthState } from '@/domain/essay.js'

/* 문항 모양은 v6 — 답변은 answer 안에 있고, 없으면 null 이다. */
const q = (id, postingId, content, usedExperienceIds = []) =>
  ({ id, postingId, sequence: 1, promptText: '', lengthLimit: 700,
     answer: content == null ? null : { content, charCount: content.length, usedExperienceIds, updatedAt: null } })

describe('essayProgress', () => {
  it('주입된 questions 를 읽는다 — 원본을 읽으면 저장해도 화면이 안 움직인다', () => {
    const before = [q(1, 9, ''), q(2, 9, null)]
    const after  = [q(1, 9, '썼다'), q(2, 9, '썼다')]
    expect(essayProgress(before).state).toBe('EMPTY')
    expect(essayProgress(after).state).toBe('DONE')
  })
  it('일부만 쓰면 WRITING — 공백만 있는 답변은 안 쓴 것이다', () => {
    expect(essayProgress([q(1, 9, 'x'), q(2, 9, '  ')])).toMatchObject({ state: 'WRITING', done: 1, total: 2 })
  })
  it('문항이 없으면 NO_QUESTIONS', () => {
    expect(essayProgress([]).state).toBe('NO_QUESTIONS')
  })
})

describe('usedIn', () => {
  it('근거로 찍혔고 AND 본문이 있는 문항만 세고, 공고 수는 postingId 로 센다', () => {
    const qs = [
      q(1, 9, '있음', [7]),
      q(2, 9, '', [7]),        // 본문 없음 → 안 셈
      q(3, 10, '있음', [7]),
      q(4, 10, '있음', [7]),   // 같은 공고 두 문항 → 문항 2, 공고 1
    ]
    expect(usedIn(7, qs)).toEqual({ questions: 3, postings: 2 })
  })
  it('답변이 null 인 문항은 건너뛴다', () => {
    expect(usedIn(7, [q(1, 9, null)])).toEqual({ questions: 0, postings: 0 })
  })
})

describe('lengthState', () => {
  const qq = { lengthLimit: 100 }
  it('80% 미만·초과는 bad, 사이는 ok, 빈칸은 idle', () => {
    expect(lengthState('', qq).tone).toBe('idle')
    expect(lengthState('a'.repeat(50), qq).tone).toBe('bad')
    expect(lengthState('a'.repeat(85), qq).tone).toBe('ok')
    expect(lengthState('a'.repeat(101), qq).tone).toBe('bad')
  })
  it('제한이 없는 문항(lengthLimit null)은 세기만 한다', () => {
    expect(lengthState('a'.repeat(300), { lengthLimit: null })).toMatchObject({ n: 300, limit: 0, tone: 'idle' })
  })
})
