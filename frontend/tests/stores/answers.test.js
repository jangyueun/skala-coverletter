import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

/* api 를 통째로 목으로 바꾼다. 스토어는 api/index.js 만 보므로 이 한 줄이면 서버가 없다. */
const save = vi.fn()
vi.mock('@/api/index.js', () => ({
  api: {
    answers: {
      applications: async () => [{ id: 101, postingId: 9 }],
      list: async () => [{ id: 1, applicationId: 101, draft: '커밋본', usedExperienceIds: [7] }],
      save: (...a) => save(...a),
    },
  },
}))

import { useAnswersStore } from '@/stores/answers.js'

describe('answers store', () => {
  let A
  beforeEach(async () => {
    setActivePinia(createPinia())
    save.mockReset()
    A = useAnswersStore()
    await A.load()
  })

  it('load() 가 문항을 받고 loaded 를 켠다', () => {
    expect(A.loaded).toBe(true)
    expect(A.questions).toHaveLength(1)
  })

  it('isDirty 는 플래그가 아니라 diff 다 — 되돌려 놓으면 깨끗하다', () => {
    A.editDraft(1, { draft: '고침' })
    expect(A.isDirty(1)).toBe(true)
    A.editDraft(1, { draft: '커밋본' })
    expect(A.isDirty(1)).toBe(false)
  })

  it('근거 경험만 바꿔도 dirty 다 — 본문과 한 번에 저장돼야 하므로', () => {
    A.editDraft(1, { usedExperienceIds: [7, 8] })
    expect(A.isDirty(1)).toBe(true)
  })

  it('저장 성공 — 커밋본이 바뀌고 버퍼는 사라진다', async () => {
    save.mockResolvedValue({ id: 1, applicationId: 101, draft: '새 글', usedExperienceIds: [7] })
    A.editDraft(1, { draft: '새 글' })
    await A.save(1)
    expect(save).toHaveBeenCalledWith(1, { draft: '새 글', usedExperienceIds: [7] })
    expect(A.questions[0].draft).toBe('새 글')
    expect(A.drafts[1]).toBeUndefined()
    expect(A.isDirty(1)).toBe(false)
    expect(A.savedAt[1]).toMatch(/^\d{2}:\d{2}$/)
  })

  it('저장 실패 — 쓴 글은 버퍼에 남고 커밋본은 그대로다', async () => {
    save.mockRejectedValue(new Error('500'))
    A.editDraft(1, { draft: '잃으면 안 되는 글' })
    await A.save(1)
    expect(A.error).toBeTruthy()
    expect(A.drafts[1].draft).toBe('잃으면 안 되는 글')     // 버퍼 유지
    expect(A.questions[0].draft).toBe('커밋본')             // 커밋본 무사
    expect(A.isDirty(1)).toBe(true)                         // 다시 저장 가능
  })

  it('버퍼가 없으면 save 는 아무것도 안 한다', async () => {
    await A.save(1)
    expect(save).not.toHaveBeenCalled()
  })
})
