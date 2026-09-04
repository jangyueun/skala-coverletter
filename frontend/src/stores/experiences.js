import { defineStore } from 'pinia'
import { api } from '@/api/index.js'

/**
 * 내 경험. 모양은 v6 GET /api/experiences 항목 —
 *   { id, title, category, startDate, endDate, situation, task, action, result, aiTaskId, competencies:[{competencyId, name, strength}] }
 * 등록·수정은 서버가 돌려준 값으로 목록을 갱신한다 — 낙관적 갱신은 안 한다.
 * 저장 응답은 { experience, reassess } 다. reassess(활성 공고마다 잡힌 MATCH 작업)는 아직 안 쓴다 —
 * 매칭을 브라우저가 계산하므로 목록이 바뀌면 카드가 바로 따라온다.
 */
export const useExperiencesStore = defineStore('experiences', {
  state: () => ({
    list: [],
    loading: false,
    error: null,
    loaded: false,
    saving: false,
  }),

  getters: {
    /** 경험이 덮고 있는 역량 id 집합 */
    taggedCompetencyIds: s => new Set(s.list.flatMap(e => e.competencies.map(c => c.competencyId))),
  },

  actions: {
    async load() {
      if (this.loading) return
      this.loading = true; this.error = null
      try { this.list = await api.experiences.list() }
      catch (e) { this.error = e }
      finally { this.loading = false; this.loaded = true }
    },

    byId(id) { return this.list.find(e => e.id === Number(id)) },
    /** 이 역량을 태그한 경험이 있나 — 필터 칩과 개수가 같은 판정을 쓴다 */
    has(experience, competencyId) { return experience.competencies.some(c => c.competencyId === competencyId) },

    async create(body) {
      this.saving = true; this.error = null
      try { const { experience } = await api.experiences.create(body); this.list.push(experience); return experience.id }
      catch (e) { this.error = e; throw e }
      finally { this.saving = false }
    },

    async update(id, body) {
      this.saving = true; this.error = null
      try {
        const { experience } = await api.experiences.update(id, body)
        const i = this.list.findIndex(e => e.id === id)
        if (i >= 0) this.list[i] = experience
      }
      catch (e) { this.error = e; throw e }
      finally { this.saving = false }
    },
  },
})
