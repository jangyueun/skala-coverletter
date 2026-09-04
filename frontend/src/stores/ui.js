import { defineStore } from 'pinia'

/** 서버가 모르는 화면 상태. 새로고침하면 사라져도 되는 것만 둔다.
    즐겨찾기는 여기 있다가 PUT /api/postings/{id}/bookmark 가 생기면서 postings 스토어로 갔다. */
export const useUiStore = defineStore('ui', {
  state: () => ({
    sort: 'match',          // 'match' | 'deadline'
  }),
})
