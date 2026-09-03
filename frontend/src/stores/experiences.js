import { defineStore } from 'pinia'
import { api } from '@/api/index.js'

/** 내 경험. 등록·수정은 서버가 돌려준 값으로 목록을 갱신한다 — 낙관적 갱신은 안 한다. */
export const useExperiencesStore = defineStore('experiences', {
  state: () => ({
    list: [],
    candidates: [],    // 포폴 인테이크 후보 — 지금은 목뿐이다
    loading: false,
    error: null,
    loaded: false,
    saving: false,
  }),

  getters: {
    /** 경험이 덮고 있는 역량 id 집합 */
    taggedCompetencyIds: s => new Set(s.list.flatMap(e => e.competencyIds)),
  },

  actions: {
    async load() {
      if (this.loading) return
      this.loading = true; this.error = null
      try {
        const [list, candidates] = await Promise.all([api.experiences.list(), api.experiences.candidates()])
        this.list = list; this.candidates = candidates
      }
      catch (e) { this.error = e }
      finally { this.loading = false; this.loaded = true }
    },

    byId(id) { return this.list.find(e => e.id === Number(id)) },

    async create(exp) {
      this.saving = true; this.error = null
      try { const row = await api.experiences.create(exp); this.list.push(row); return row.id }
      catch (e) { this.error = e; throw e }
      finally { this.saving = false }
    },

    async update(id, patch) {
      this.saving = true; this.error = null
      try {
        const row = await api.experiences.update(id, patch)
        const i = this.list.findIndex(e => e.id === id)
        if (i >= 0) this.list[i] = row
      }
      catch (e) { this.error = e; throw e }
      finally { this.saving = false }
    },
  },
})
