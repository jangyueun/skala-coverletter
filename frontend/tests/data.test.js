import { describe, it, expect } from 'vitest'
import { DATA } from '@/api/mock/data.js'
import { deadlineAt } from '@/domain/deadline.js'
import { CATEGORIES } from '@/domain/competency.js'
import { EXPERIENCE_CATEGORIES, periodValid } from '@/domain/experience.js'

/* 목 데이터가 사전·명세와 어긋나면 빌드가 막힌다.
   posting 9 가 재번호 전 id 를 들고 있어 백엔드 공고에 Vue.js 가 그려진 적이 있다 —
   id 가 존재하기만 하면 조용히 통과하므로 사람 눈으로는 못 잡는다. 여기서 잡는다.
   모양은 docs/api-spec-v6.md 의 DTO 다 — 백엔드가 붙는 날 스토어에 변환이 생기지 않게. */
describe('mock data 정합성', () => {
  const ids = new Set(DATA.competencies.map(c => c.id))
  const cats = new Set(CATEGORIES.map(c => c.k))
  const expCats = new Set(EXPERIENCE_CATEGORIES.map(c => c.k))

  it('공고의 요구 역량이 전부 사전에 있고 가중치가 0~1 이다', () => {
    for (const p of DATA.postings)
      for (const r of p.requiredCompetencies) {
        expect(ids.has(r.competencyId), `공고 ${p.id} ${p.company} → ${r.competencyId}`).toBe(true)
        expect(r.weight, `공고 ${p.id} 가중치`).toBeGreaterThanOrEqual(0)
        expect(r.weight, `공고 ${p.id} 가중치`).toBeLessThanOrEqual(1)
        expect(r.evidenceLine, `공고 ${p.id} 근거`).toBeTruthy()
      }
  })

  it('경험이 태그한 역량이 전부 사전에 있고 강도가 0~1 이다', () => {
    for (const e of DATA.experiences)
      for (const c of e.competencies) {
        expect(ids.has(c.competencyId), `경험 ${e.id} ${e.title} → ${c.competencyId}`).toBe(true)
        expect(c.strength, `경험 ${e.id} 강도`).toBeGreaterThanOrEqual(0)
        expect(c.strength, `경험 ${e.id} 강도`).toBeLessThanOrEqual(1)
      }
  })

  it('한 공고 안에서 같은 역량을 두 번 요구하지 않는다', () => {
    for (const p of DATA.postings) {
      const seen = p.requiredCompetencies.map(r => r.competencyId)
      expect(new Set(seen).size, `공고 ${p.id}`).toBe(seen.length)
    }
  })

  it('사전 이름이 겹치지 않고 범주가 다섯 중 하나다', () => {
    const names = DATA.competencies.map(c => c.name)
    expect(new Set(names).size).toBe(names.length)
    for (const c of DATA.competencies) expect(cats.has(c.category), c.name).toBe(true)
  })

  it('마감은 시각 포함 ISO(오프셋 있음)이고 파싱되며 상태는 ACTIVE·CLOSED 다', () => {
    for (const p of DATA.postings) {
      expect(p.deadline, `공고 ${p.id}`).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2}$/)
      expect(Number.isNaN(deadlineAt(p.deadline).getTime()), `공고 ${p.id}`).toBe(false)
      expect(['ACTIVE', 'CLOSED'], `공고 ${p.id} 상태`).toContain(p.status)
      expect(p.content, `공고 ${p.id} 원문`).toBeTruthy()
    }
  })

  it('경험 분류는 여섯 코드 중 하나고 기간은 YYYY-MM-01 이며 종료가 시작보다 앞서지 않는다', () => {
    for (const e of DATA.experiences) {
      expect(expCats.has(e.category), `경험 ${e.id} 분류 ${e.category}`).toBe(true)
      for (const d of [e.startDate, e.endDate]) if (d) expect(d, `경험 ${e.id} 기간`).toMatch(/^\d{4}-\d{2}-01$/)
      expect(periodValid(e.startDate, e.endDate), `경험 ${e.id} 기간 순서`).toBe(true)
      expect(e.result, `경험 ${e.id} 결과(R)는 필수`).toBeTruthy()
    }
  })

  it('모든 공고에 문항이 있고 순번이 1부터 이어진다 — 문항 없는 상황은 없애기로 했다', () => {
    for (const p of DATA.postings) {
      const qs = DATA.questions.filter(q => q.postingId === p.id).sort((a, b) => a.sequence - b.sequence)
      expect(qs.length, `공고 ${p.id} 문항`).toBeGreaterThan(0)
      expect(qs.map(q => q.sequence), `공고 ${p.id} 순번`).toEqual(qs.map((_, i) => i + 1))
    }
    for (const q of DATA.questions) {
      expect(DATA.postings.some(p => p.id === q.postingId), `문항 ${q.id} 의 공고 ${q.postingId}`).toBe(true)
      if (q.answer) {
        for (const id of q.answer.usedExperienceIds)
          expect(DATA.experiences.some(e => e.id === id), `문항 ${q.id} 근거 경험 ${id}`).toBe(true)
      }
    }
  })

  it('AI 초안은 있는 문항에만 붙어 있다', () => {
    for (const id of Object.keys(DATA.aiDrafts))
      expect(DATA.questions.some(q => q.id === Number(id)), `초안 문항 ${id}`).toBe(true)
  })

  it('인테이크 후보의 제안 역량이 사전에 있고 분류·기간이 경험과 같은 규칙이다', () => {
    for (const c of DATA.intakeCandidates) {
      for (const id of c.suggestedCompetencyIds) expect(ids.has(id), `후보 ${c.key} → ${id}`).toBe(true)
      expect(expCats.has(c.category), `후보 ${c.key} 분류`).toBe(true)
      expect(periodValid(c.startDate, c.endDate), `후보 ${c.key} 기간`).toBe(true)
      if (c.duplicateOfExperienceId != null)
        expect(DATA.experiences.some(e => e.id === c.duplicateOfExperienceId), `후보 ${c.key} 중복 대상`).toBe(true)
    }
  })
})
