import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const bookmark = vi.fn()
vi.mock('@/api/index.js', () => ({
  api: {
    postings: {
      list: async () => [
        { id: 9,  company: 'A', position: 'p', deadline: '2099-01-01T00:00:00+09:00', status: 'ACTIVE', bookmarked: false, requiredCompetencies: [] },
        { id: 10, company: 'B', position: 'p', deadline: '2000-01-01T00:00:00+09:00', status: 'CLOSED', bookmarked: true,  requiredCompetencies: [] },
      ],
      dictionary: async () => [{ id: 1, name: 'A', category: 'ROLE', aliases: [] }],
      bookmark: (...a) => bookmark(...a),
    },
  },
}))

import { usePostingsStore } from '@/stores/postings.js'

describe('postings store', () => {
  let P
  beforeEach(async () => {
    setActivePinia(createPinia())
    bookmark.mockReset()
    P = usePostingsStore()
    await P.load()
  })

  it('load() 가 공고와 사전을 같이 받고, live 는 마감 안 지난 것만', () => {
    expect(P.loaded).toBe(true)
    expect(P.list).toHaveLength(2)
    expect(P.competencies).toHaveLength(1)
    expect(P.live.map(p => p.id)).toEqual([9])
  })

  it('toggleBookmark 는 즉시 뒤집고 서버에 PUT 한다 — 서버 응답값으로 확정', async () => {
    let resolve
    bookmark.mockReturnValue(new Promise(r => { resolve = r }))
    const done = P.toggleBookmark(9)
    expect(P.byId(9).bookmarked).toBe(true)                   // 응답 전에 이미 별이 켜진다
    expect(bookmark).toHaveBeenCalledWith(9, true)
    resolve({ postingId: 9, bookmarked: true })
    await done
    expect(P.byId(9).bookmarked).toBe(true)
  })

  it('서버가 거절하면 되돌리고 목록은 그대로다 — error 로 화면을 덮지 않는다', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    bookmark.mockRejectedValue(new Error('500'))
    await P.toggleBookmark(10)
    expect(P.byId(10).bookmarked).toBe(true)                  // 원래 true 였다
    expect(P.error).toBeNull()
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('없는 공고는 무시한다', async () => {
    await P.toggleBookmark(999)
    expect(bookmark).not.toHaveBeenCalled()
  })
})
