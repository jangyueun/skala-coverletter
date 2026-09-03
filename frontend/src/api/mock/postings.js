import { DATA } from './data.js'
import { delay, clone } from './_delay.js'
import { ApiError } from '../client.js'

/* 실제(api/real/postings.js)는 목록 DTO(match·essay·bookmarked)에 상세(content·requiredCompetencies·related)를
   합쳐서 준다 — 매칭을 브라우저가 계산하므로 요구 역량이 카드마다 필요하다. 목은 data.js 가 이미 상세 모양이라
   bookmarked 만 붙이면 같은 꼴이다. match·essay 는 안 싣는다 — 서버도 MATCH 워커가 없어 match 를 null 로 주고,
   essay 는 문항 스토어가 전 공고를 들고 있어 브라우저가 센다. */

const dict = new Map(DATA.competencies.map(c => [c.id, c]))

/** 세션 안에서만 사는 즐겨찾기. 실제는 bookmarks 테이블이다. */
const bookmarks = new Set()

/** 상세 DTO 는 요구 역량에 name·category 를 붙여 준다. data.js 는 id 만 든다 — 이름이 두 곳에 있으면 한쪽만 고쳐진다. */
const decorate = p => ({
  ...p,
  bookmarked: bookmarks.has(p.id),
  requiredCompetencies: p.requiredCompetencies.map(r => {
    const c = dict.get(r.competencyId)
    return { ...r, name: c?.name ?? null, category: c?.category ?? null }
  }),
})

export async function list()       { await delay('postings'); return clone(DATA.postings.map(decorate)) }
/** GET /api/competencies — 범주 라벨은 domain/competency.js 상수다 */
export async function dictionary() { await delay('postings'); return clone(DATA.competencies) }

export async function detail(id) {
  await delay('postings')
  const p = DATA.postings.find(p => p.id === Number(id))
  if (!p) throw new ApiError(404, '공고를 찾을 수 없습니다', { code: 'POSTING_NOT_FOUND' })
  return clone(decorate(p))
}

/** PUT /api/postings/{id}/bookmark → { postingId, bookmarked } */
export async function bookmark(id, bookmarked) {
  await delay('postings')
  const postingId = Number(id)
  if (!DATA.postings.some(p => p.id === postingId)) throw new ApiError(404, '공고를 찾을 수 없습니다', { code: 'POSTING_NOT_FOUND' })
  bookmarked ? bookmarks.add(postingId) : bookmarks.delete(postingId)
  return { postingId, bookmarked: !!bookmarked }
}
