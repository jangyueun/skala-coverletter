import { defineStore } from 'pinia'

/** 서버가 모르는 화면 상태. 새로고침하면 사라져도 되는 것만 둔다. */
export const useUiStore = defineStore('ui', {
  state: () => ({
    sort: 'match',          // 'match' | 'deadline'
    bookmarks: new Set(),   // TODO 백엔드에 즐겨찾기 API 가 생기면 answers 옆으로 옮긴다
  }),
  actions: {
    toggleBookmark(id) {
      // Set 은 Vue 반응성에서 교체해야 갱신된다. mutate 만 하면 화면이 안 바뀐다.
      const next = new Set(this.bookmarks)
      next.has(id) ? next.delete(id) : next.add(id)
      this.bookmarks = next
    },
  },
})
