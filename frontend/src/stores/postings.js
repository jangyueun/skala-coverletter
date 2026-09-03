import { defineStore } from 'pinia'
import { api } from '@/api/index.js'
import { isClosed } from '@/domain/deadline.js'

/**
 * 공고와 역량 사전. 둘 다 읽기 전용 참조 데이터라 한 스토어에 둔다.
 * 사전이 없으면 매칭을 못 하므로 load() 가 둘을 같이 받는다.
 *
 * 공고 모양은 v6 상세 DTO + 목록 DTO 의 합집합이다 —
 *   { id, company, position, deadline, status, sourceUrl, content, bookmarked,
 *     requiredCompetencies:[{ competencyId, name, category, weight, evidenceLine }],
 *     related?, match?, essay? }
 * match·essay 는 서버 목록에만 있다(목은 없다). 카드가 어느 쪽을 쓰는지는 derived.js 가 정한다.
 *
 * 즐겨찾기는 공고에 붙은 값(bookmarked)이라 여기서 뒤집는다 — 사용자마다 다른 값이지만
 * 서버가 공고 DTO 에 실어 주므로 따로 스토어를 둘 이유가 없다.
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

    /**
     * 별은 누르는 즉시 바뀐다 — 300~800ms 뒤에 바뀌면 안 눌린 줄 알고 한 번 더 누른다.
     * 서버가 거절하면 되돌린다. 목록 전체를 ErrorNote 로 덮지는 않는다 —
     * 즐겨찾기 하나 실패했다고 공고 목록이 사라지면 그게 더 큰 고장이다.
     */
    async toggleBookmark(id) {
      const p = this.byId(id)
      if (!p) return
      const next = !p.bookmarked
      p.bookmarked = next
      try { const r = await api.postings.bookmark(p.id, next); p.bookmarked = !!r.bookmarked }
      catch (e) { p.bookmarked = !next; console.warn(`[postings] 공고 ${p.id} 즐겨찾기 저장 실패`, e) }
    },
  },
})
