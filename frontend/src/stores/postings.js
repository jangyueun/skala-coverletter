import { defineStore } from 'pinia'
import { api } from '@/api/index.js'
import { isClosed } from '@/domain/deadline.js'

/**
 * 공고와 역량 사전. 둘 다 읽기 전용 참조 데이터라 한 스토어에 둔다.
 * 사전이 없으면 매칭을 못 하므로 load() 가 둘을 같이 받는다.
 */
export const usePostingsStore = defineStore('postings', {
  state: () => ({
    list: [],
    competencies: [],
    loading: false,
    error: null,
    loaded: false,
  }),

  getters: {
    /** 마감이 지나지 않은 공고만. 지난 공고를 목록에 두면 할 일이 아닌 것이 섞인다. */
    live: s => s.list.filter(p => !isClosed(p.deadline)),
  },

  actions: {
    async load() {
      if (this.loading) return
      this.loading = true; this.error = null
      try {
        const [list, competencies] = await Promise.all([api.postings.list(), api.postings.dictionary()])
        this.list = list; this.competencies = competencies
      }
      catch (e) { this.error = e }
      finally { this.loading = false; this.loaded = true }
    },

    byId(id) { return this.list.find(p => p.id === Number(id)) },
    competencyById(id) { return this.competencies.find(c => c.id === Number(id)) },
  },
})
