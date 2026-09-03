import { describe, it, expect, vi, beforeEach } from 'vitest'

/* client 만 바꿔치기한다. real 어댑터가 어떤 경로·본문으로 부르는지와,
   서버 DTO 를 스토어가 기대하는 모양으로 어떻게 맞추는지가 이 파일이 지키는 것이다. */
const get = vi.fn(), put = vi.fn(), post = vi.fn()
vi.mock('@/api/client.js', () => ({
  client: { get: (...a) => get(...a), put: (...a) => put(...a), post: (...a) => post(...a) },
  ApiError: class extends Error {},
}))

import * as postings from '@/api/real/postings.js'
import * as answers from '@/api/real/answers.js'
import * as experiences from '@/api/real/experiences.js'

beforeEach(() => { get.mockReset(); put.mockReset(); post.mockReset() })

describe('real postings.list', () => {
  const item = id => ({ id, company: `C${id}`, position: 'P', deadline: '2026-09-12T18:00:00+09:00', status: 'ACTIVE',
    bookmarked: id === 2, match: null, essay: { state: 'EMPTY', answered: 0, total: 2 } })
  const detailOf = id => ({ id, company: `C${id}`, position: 'P', deadline: '2026-09-12T18:00:00+09:00', status: 'ACTIVE',
    sourceUrl: 'u', content: `본문 ${id}`, bookmarked: id === 2,
    requiredCompetencies: [{ competencyId: 3, name: 'API', category: 'ROLE', weight: 0.9, evidenceLine: 'e' }],
    related: { sameCompany: [], similar: [] } })

  it('전 페이지를 받고(includeClosed) 상세를 합쳐 준다 — 목록의 match·essay 와 상세의 requiredCompetencies 가 한 객체에', async () => {
    get.mockImplementation(async path => {
      if (path.startsWith('/postings?')) {
        const page = Number(new URLSearchParams(path.split('?')[1]).get('page'))
        return { items: page === 0 ? [item(1), item(2)] : [item(3)], page, size: 100, totalCount: 3 }
      }
      return detailOf(Number(path.split('/').pop()))
    })
    const list = await postings.list()
    expect(list.map(p => p.id)).toEqual([1, 2, 3])
    expect(get.mock.calls.filter(([p]) => p.startsWith('/postings?'))).toHaveLength(2)   // 100개씩 두 페이지
    expect(get.mock.calls[0][0]).toContain('includeClosed=true')
    expect(list[0].requiredCompetencies[0].competencyId).toBe(3)                          // 상세
    expect(list[0].essay).toEqual({ state: 'EMPTY', answered: 0, total: 2 })              // 목록
    expect(list[0].content).toBe('본문 1')
    expect(list[1].bookmarked).toBe(true)
  })

  it('공고가 0건이면 상세를 부르지 않는다', async () => {
    get.mockResolvedValue({ items: [], page: 0, size: 100, totalCount: 0 })
    expect(await postings.list()).toEqual([])
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('bookmark 는 PUT /postings/{id}/bookmark { bookmarked }', async () => {
    put.mockResolvedValue({ postingId: 9, bookmarked: true })
    await expect(postings.bookmark(9, true)).resolves.toEqual({ postingId: 9, bookmarked: true })
    expect(put).toHaveBeenCalledWith('/postings/9/bookmark', { bookmarked: true })
  })

  it('dictionary 는 GET /competencies', async () => {
    get.mockResolvedValue([])
    await postings.dictionary()
    expect(get).toHaveBeenCalledWith('/competencies')
  })
})

describe('real answers', () => {
  it('list 는 빈 배열 — 전 공고 문항 API 가 없다', async () => {
    expect(await answers.list()).toEqual([])
    expect(get).not.toHaveBeenCalled()
  })
  it('questions 는 GET /postings/{id}/questions', async () => {
    get.mockResolvedValue([{ id: 31, sequence: 1, promptText: 'q', lengthLimit: 700, answer: null }])
    await answers.questions(9)
    expect(get).toHaveBeenCalledWith('/postings/9/questions')
  })
  it('save 는 PUT /questions/{id}/answer 에 세 필드만 보낸다', async () => {
    put.mockResolvedValue({})
    await answers.save(31, { content: 'c', usedExperienceIds: [1], draftTaskId: 821, junk: true })
    expect(put).toHaveBeenCalledWith('/questions/31/answer', { content: 'c', usedExperienceIds: [1], draftTaskId: 821 })
  })
})

describe('real experiences', () => {
  it('create 는 intakeTaskId 를 그대로, update 는 떼고 보낸다 — 출처는 수정으로 안 바뀐다', async () => {
    post.mockResolvedValue({}); put.mockResolvedValue({})
    const body = { title: 't', category: 'TEAM_PROJECT', competencies: [{ competencyId: 1, strength: 0.7 }], intakeTaskId: 790 }
    await experiences.create(body)
    expect(post).toHaveBeenCalledWith('/experiences', body)
    await experiences.update(5, body)
    const { intakeTaskId, ...rest } = body
    expect(put).toHaveBeenCalledWith('/experiences/5', rest)
  })
})
