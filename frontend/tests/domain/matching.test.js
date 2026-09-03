import { describe, it, expect } from 'vitest'
import { computeMatch, topGap, SCORE, strLabel } from '@/domain/matching.js'

const DICT = [
  { id: 1, name: 'A', category: 'ROLE' },
  { id: 2, name: 'B', category: 'TECH' },
  { id: 3, name: 'C', category: 'SOFT' },
]
const posting = { id: 9, required: [
  { competencyId: 1, weight: 0.9, evidence: 'a' },
  { competencyId: 2, weight: 0.6, evidence: 'b' },
]}

describe('computeMatch', () => {
  it('score 는 강도 합, 1 에서 자른다', () => {
    const exps = [
      { id: 10, competencyIds: [1], strength: { 1: 0.7 } },
      { id: 11, competencyIds: [1], strength: { 1: 0.7 } },   // 0.7 + 0.7 = 1.4 → 1
    ]
    const { rows } = computeMatch(posting, exps, DICT)
    expect(rows[0].score).toBe(1)
    expect(rows[0].evid).toHaveLength(2)
  })

  it('overall 은 가중 평균 — 다 덮어도 근거가 약하면 100% 가 아니다', () => {
    const exps = [{ id: 10, competencyIds: [1, 2], strength: { 1: 0.6, 2: 1.0 } }]
    const { rows, overall } = computeMatch(posting, exps, DICT)
    expect(rows.every(r => !r.isGap)).toBe(true)            // 둘 다 "덮음"
    expect(overall).toBeCloseTo((0.9 * 0.6 + 0.6 * 1.0) / 1.5, 5)   // 0.76
  })

  it('강도를 안 정하면 DEFAULT_STRENGTH 로 친다', () => {
    const exps = [{ id: 10, competencyIds: [1], strength: {} }]
    expect(computeMatch(posting, exps, DICT).rows[0].score).toBe(SCORE.DEFAULT_STRENGTH)
  })

  it('근거가 없거나 GAP 미만이면 갭이다', () => {
    const exps = [{ id: 10, competencyIds: [2], strength: { 2: 0.4 } }]
    const { rows } = computeMatch(posting, exps, DICT)
    expect(rows[0].isGap).toBe(true)     // 근거 없음
    expect(rows[1].isGap).toBe(true)     // 0.4 < 0.45
  })

  it('사전에 없는 id 는 조용히 지나가지 않고 터진다', () => {
    // 오늘 posting 9 가 옛 번호로 엉뚱한 역량을 그린 사고 — 이제는 여기서 잡힌다
    const bad = { id: 1, required: [{ competencyId: 99, weight: 1, evidence: '' }] }
    expect(() => computeMatch(bad, [], DICT)).toThrow(/99/)
  })
})

describe('topGap', () => {
  it('여러 공고에서 같이 비는 역량을, 동점이면 가중치 합이 큰 쪽을 준다', () => {
    const p2 = { id: 10, required: [{ competencyId: 2, weight: 0.9, evidence: '' }, { competencyId: 3, weight: 0.5, evidence: '' }] }
    const p3 = { id: 11, required: [{ competencyId: 2, weight: 0.9, evidence: '' }, { competencyId: 3, weight: 0.5, evidence: '' }] }
    const g = topGap([p2, p3], [], DICT)
    expect(g.competency.id).toBe(2)
    expect(g.postingCount).toBe(2)
  })
  it('갭이 없으면 null', () => {
    const exps = [{ id: 1, competencyIds: [1, 2], strength: { 1: 0.9, 2: 0.9 } }]
    expect(topGap([posting], exps, DICT)).toBeNull()
  })
})

describe('strLabel', () => {
  it('약 / 중 / 강 경계', () => {
    expect(strLabel(0.4)).toBe('약'); expect(strLabel(0.6)).toBe('중'); expect(strLabel(0.8)).toBe('강')
  })
})
