/* 공고·역량 사전 — 백엔드 JobPostingController · CompetencyController (docs/api-spec-v6.md §2·§3).
 *
 *   GET /api/competencies                      [{ id, name, category, aliases }]
 *   GET /api/postings?…&page&size              { items:[{ id, company, position, deadline, status, bookmarked, match, essay }], page, size, totalCount }
 *   GET /api/postings/{id}                     { …목록 항목 + sourceUrl, content, requiredCompetencies, related }
 *   PUT /api/postings/{id}/bookmark            { postingId, bookmarked }
 *
 * 목(api/mock/postings.js)과 시그니처가 같다 — 스토어는 어느 쪽인지 모른다. */

import { client } from '../client.js'

/** 서버 MAX_PAGE_SIZE. 넘기면 400 INVALID_LIST_QUERY 다. */
const PAGE = 100
/** 상세를 동시에 몇 개까지 받나. 세션 DB 풀이 2개라(application.yml) 무작정 쏘면 서버에서 줄을 선다. */
const DETAIL_CONCURRENCY = 4

/**
 * 공고 전부 — 마감 지난 것까지(MY 화면의 "마감 지남" 이 본다). 정렬은 브라우저가 다시 하므로 deadline 고정.
 *
 * 목록 항목에 **상세를 합쳐서** 돌려준다. 목록 DTO 에는 requiredCompetencies 가 없는데, 카드의 매칭률과
 * 역량 태그·필터가 전부 그걸로 브라우저에서 계산된다(domain/matching.js). 서버가 계산한 match 는 MATCH 워커가
 * 아직 없어 늘 null 이라 대신 쓸 수도 없다.
 *
 * 워커가 붙는 날 — 아래 상세 합치기를 지우고 derived.matchFor 가 posting.match 를 우선하면 목록 한 번으로 끝난다.
 * 그때까지는 공고 수 + 1 번 요청이다(시드 10건 기준 11번). */
export async function list() {
  const items = []
  for (let page = 0; ; page++) {
    const r = await client.get(`/postings?includeClosed=true&sort=deadline&page=${page}&size=${PAGE}`)
    items.push(...r.items)
    if (!r.items.length || items.length >= r.totalCount) break
  }
  const details = await mapLimit(items, DETAIL_CONCURRENCY, it => detail(it.id))
  // 상세가 뒤에 와서 같은 키(id·company·deadline…)는 상세 값이고, match·essay 는 목록에만 있어 그대로 남는다.
  return items.map((it, i) => ({ ...it, ...details[i] }))
}

/** GET /api/competencies — 범주 라벨은 domain/competency.js 상수다 */
export const dictionary = () => client.get('/competencies')

export const detail = id => client.get(`/postings/${id}`)

export const bookmark = (id, bookmarked) => client.put(`/postings/${id}/bookmark`, { bookmarked })

/** Promise.all 인데 동시에 limit 개만 돈다. 순서는 입력과 같다. */
async function mapLimit(items, limit, fn) {
  const out = new Array(items.length)
  let next = 0
  const worker = async () => {
    for (let i = next++; i < items.length; i = next++) out[i] = await fn(items[i], i)
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker))
  return out
}
