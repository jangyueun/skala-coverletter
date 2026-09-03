import { DATA } from './data.js'
import { delay, clone } from './_delay.js'

/* v6 DTO 와의 차이 하나 —
   실제 GET /api/postings 목록에는 content·requiredCompetencies 가 없고, 대신 서버가 계산한
   match·essay·bookmarked 가 실린다. 지금 화면은 그 셋을 브라우저에서 파생하므로(stores/derived.js)
   목은 상세 DTO 모양(content·requiredCompetencies 포함)을 목록으로 준다.
   백엔드가 붙는 날 목록은 서버 값을 쓰고 상세는 GET /api/postings/{id} 로 따로 받는다. */

const dict = new Map(DATA.competencies.map(c => [c.id, c]))

/** 상세 DTO 는 요구 역량에 name·category 를 붙여 준다. data.js 는 id 만 든다 — 이름이 두 곳에 있으면 한쪽만 고쳐진다. */
const decorate = p => ({
  ...p,
  requiredCompetencies: p.requiredCompetencies.map(r => {
    const c = dict.get(r.competencyId)
    return { ...r, name: c?.name ?? null, category: c?.category ?? null }
  }),
})

export async function list()       { await delay('postings'); return clone(DATA.postings.map(decorate)) }
/** GET /api/competencies — 범주 라벨은 domain/competency.js 상수다 */
export async function dictionary() { await delay('postings'); return clone(DATA.competencies) }
