import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

/* api 를 통째로 목으로 바꾼다. 스토어는 api/index.js 만 보므로 이 한 줄이면 서버가 없다.
   모양은 v6 — 문항의 answer 가 커밋본이고, save 는 PUT /api/questions/{id}/answer 응답을 돌려준다. */
const save = vi.fn()
vi.mock('@/api/index.js', () => ({
  api: {
    answers: {
      list: async () => [
        { id: 1, postingId: 9, sequence: 2, promptText: '', lengthLimit: 700,
          answer: { content: '커밋본', charCount: 3, usedExperienceIds: [7], updatedAt: '2026-09-02T21:14:00+09:00' } },
        { id: 2, postingId: 9, sequence: 1, promptText: '', lengthLimit: 700, answer: null },
      ],
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
    expect(A.questions).toHaveLength(2)
  })

  it('questionsFor 는 공고의 문항을 순번대로 준다', () => {
    expect(A.questionsFor(9).map(q => q.id)).toEqual([2, 1])
    expect(A.questionsFor(10)).toEqual([])
  })

  it('답변이 null 인 문항은 빈 답변으로 읽힌다 — 화면이 null 을 만나지 않는다', () => {
    expect(A.draftOf(2)).toEqual({ content: '', usedExperienceIds: [], draftTaskId: null })
    expect(A.isDirty(2)).toBe(false)
  })

  it('isDirty 는 플래그가 아니라 diff 다 — 되돌려 놓으면 깨끗하다', () => {
    A.editDraft(1, { content: '고침' })
    expect(A.isDirty(1)).toBe(true)
    A.editDraft(1, { content: '커밋본' })
    expect(A.isDirty(1)).toBe(false)
  })

  it('근거 경험만 바꿔도 dirty 다 — 본문과 한 번에 저장돼야 하므로', () => {
    A.editDraft(1, { usedExperienceIds: [7, 8] })
    expect(A.isDirty(1)).toBe(true)
  })

  it('저장 성공 — 커밋본이 바뀌고 버퍼는 사라지고 시각은 서버 updatedAt 이다', async () => {
    save.mockResolvedValue({ questionId: 1, content: '새 글', charCount: 3, usedExperienceIds: [7], aiTaskId: null, updatedAt: '2026-09-03T15:10:42+09:00' })
    A.editDraft(1, { content: '새 글' })
    await A.save(1)
    expect(save).toHaveBeenCalledWith(1, { content: '새 글', usedExperienceIds: [7], draftTaskId: null })
    expect(A.questions[0].answer.content).toBe('새 글')
    expect(A.questions[0].answer.charCount).toBe(3)
    expect(A.drafts[1]).toBeUndefined()
    expect(A.isDirty(1)).toBe(false)
    expect(A.savedAt[1]).toBe(
      `${String(new Date('2026-09-03T15:10:42+09:00').getHours()).padStart(2, '0')}:10`)
  })

  it('AI 초안을 넣으면 draftTaskId 가 저장까지 따라간다 — 손으로 고쳐도 남는다', async () => {
    save.mockResolvedValue({ questionId: 2, content: '초안 고침', charCount: 5, usedExperienceIds: [7], aiTaskId: 821, updatedAt: null })
    A.editDraft(2, { content: '초안', draftTaskId: 821, usedExperienceIds: [7] })
    A.editDraft(2, { content: '초안 고침' })
    await A.save(2)
    expect(save).toHaveBeenCalledWith(2, { content: '초안 고침', usedExperienceIds: [7], draftTaskId: 821 })
    expect(A.questions[1].answer.content).toBe('초안 고침')   // null 이던 답변이 생긴다
  })

  it('저장 실패 — 쓴 글은 버퍼에 남고 커밋본은 그대로다', async () => {
    save.mockRejectedValue(new Error('500'))
    A.editDraft(1, { content: '잃으면 안 되는 글' })
    await A.save(1)
    expect(A.error).toBeTruthy()
    expect(A.drafts[1].content).toBe('잃으면 안 되는 글')     // 버퍼 유지
    expect(A.questions[0].answer.content).toBe('커밋본')      // 커밋본 무사
    expect(A.isDirty(1)).toBe(true)                           // 다시 저장 가능
  })

  it('버퍼가 없으면 save 는 아무것도 안 한다', async () => {
    await A.save(1)
    expect(save).not.toHaveBeenCalled()
  })
})
