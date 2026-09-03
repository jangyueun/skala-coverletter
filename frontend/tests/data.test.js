import { describe, it, expect } from 'vitest'
import { DATA } from '@/api/mock/data.js'
import { deadlineAt } from '@/domain/deadline.js'
import { CATEGORIES } from '@/domain/competency.js'

/* 목 데이터가 사전과 어긋나면 빌드가 막힌다.
   오늘 posting 9 가 재번호 전 id 를 들고 있어 백엔드 공고에 Vue.js 가 그려졌다 —
   id 가 존재하기만 하면 조용히 통과하므로 사람 눈으로는 못 잡는다. 여기서 잡는다. */
describe('mock data 정합성', () => {
  const ids = new Set(DATA.competencies.map(c => c.id))
  const cats = new Set(CATEGORIES.map(c => c.k))

  it('공고의 요구 역량이 전부 사전에 있다', () => {
    for (const p of DATA.postings)
      for (const r of p.required)
        expect(ids.has(r.competencyId), `공고 ${p.id} ${p.company} → ${r.competencyId}`).toBe(true)
  })

  it('경험이 태그한 역량이 전부 사전에 있다', () => {
    for (const e of DATA.experiences)
      for (const c of e.competencyIds)
        expect(ids.has(c), `경험 ${e.id} ${e.title} → ${c}`).toBe(true)
  })

  it('한 공고 안에서 같은 역량을 두 번 요구하지 않는다', () => {
    for (const p of DATA.postings) {
      const seen = p.required.map(r => r.competencyId)
      expect(new Set(seen).size, `공고 ${p.id}`).toBe(seen.length)
    }
  })

  it('사전 이름이 겹치지 않고 범주가 다섯 중 하나다', () => {
    const names = DATA.competencies.map(c => c.name)
    expect(new Set(names).size).toBe(names.length)
    for (const c of DATA.competencies) expect(cats.has(c.category), c.name).toBe(true)
  })

  it('마감은 "YYYY-MM-DD HH:mm" 이고 파싱된다', () => {
    for (const p of DATA.postings) {
      expect(p.deadline, `공고 ${p.id}`).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
      expect(Number.isNaN(deadlineAt(p.deadline).getTime()), `공고 ${p.id}`).toBe(false)
    }
  })

  it('모든 공고에 지원서와 문항이 있다 — 문항 없는 상황은 없애기로 했다', () => {
    for (const p of DATA.postings) {
      const app = DATA.applications.find(a => a.postingId === p.id)
      expect(app, `공고 ${p.id} 지원서`).toBeTruthy()
      expect(DATA.questions.filter(q => q.applicationId === app.id).length, `공고 ${p.id} 문항`).toBeGreaterThan(0)
    }
  })
})
