/* 매칭 계산. 순수 함수 — 서버도 스토어도 모른다.
 *
 * 필요한 데이터는 전부 인자로 받는다. 기본 인자로 목 데이터를 잡던 시절에
 * 두 번 사고가 났다 — 스토어 사본이 아니라 원본을 읽어 저장해도 화면이 안 움직였고,
 * 옛 id 가 조용히 통과해 엉뚱한 역량을 그렸다. 안 받으면 터지는 게 맞다. */

/** 판정 경계값. 화면 여기저기에 흩어 놓으면 한쪽만 고쳐진다. */
export const SCORE = {
  GAP: 0.45,          // 이 아래면 "갭" 으로 본다
  WEAK: 0.70,
  STRONG: 0.90,
  RECOMMEND: 0.85,    // 이 위면 지원 권장
  CONDITIONAL: 0.62,
  DEFAULT_STRENGTH: 0.60,
  PICK_STRENGTH: 0.70,
}

/** 약 / 중 / 강 — 내부값은 연속이지만 사람에게는 3단계로만 보여준다. */
export const STR = [
  { v: 0.4, lab: '약' },
  { v: 0.7, lab: '중' },
  { v: 0.9, lab: '강' },
]
export const strLabel = v => (v >= 0.8 ? '강' : v >= 0.6 ? '중' : '약')

/**
 * 공고 하나에 대한 나의 매칭.
 *
 * score   = min(1, Σ strength) — 같은 역량을 여러 경험이 뒷받침하면 확신이 오르지만 1을 넘지 않는다.
 * overall = Σ(weight × score) / Σ weight — 가중 평균. 무겁게 요구되는 것을 못 채우면 크게 깎인다.
 * isGap   = 근거 없음 또는 score < GAP. "덮었다" 는 이 이진 판정이고, overall 과는 기준이 다르다.
 *
 * @param {object}   posting       required: [{competencyId, weight, evidence}]
 * @param {object[]} experiences   competencyIds, strength{[id]: 0~1}
 * @param {object[]} competencies  역량 사전
 */
export function computeMatch(posting, experiences, competencies) {
  const byId = id => competencies.find(c => c.id === id)
  const rows = posting.required.map(r => {
    const comp = byId(r.competencyId)
    if (!comp) throw new Error(`공고 ${posting.id} 의 요구 역량 ${r.competencyId} 이 사전에 없다`)
    const evid = experiences.filter(e => e.competencyIds.includes(r.competencyId))
    const strength = evid.reduce((a, e) => a + (e.strength?.[r.competencyId] ?? SCORE.DEFAULT_STRENGTH), 0)
    const score = Math.min(1, strength)
    return { ...r, comp, evid, score, isGap: evid.length === 0 || score < SCORE.GAP }
  })
  const wsum = rows.reduce((a, r) => a + r.weight, 0)
  const overall = wsum ? rows.reduce((a, r) => a + r.weight * r.score, 0) / wsum : 0
  return { rows, overall }
}

/**
 * 여러 공고에서 동시에 갭인 역량 — "다음에 뭘 채워야 하나".
 *
 * 평균 매칭률을 대신한다. 평균은 행동으로 이어지지 않는 숫자였다.
 * 동점이면 가중치 합이 큰 쪽을 먼저 — 같은 3건이어도 더 무겁게 요구되는 것이 있다.
 */
export function topGap(postings, experiences, competencies) {
  const cnt = new Map()
  const weight = new Map()
  postings.forEach(p => {
    computeMatch(p, experiences, competencies).rows.filter(r => r.isGap).forEach(r => {
      cnt.set(r.competencyId, (cnt.get(r.competencyId) || 0) + 1)
      weight.set(r.competencyId, (weight.get(r.competencyId) || 0) + r.weight)
    })
  })
  if (!cnt.size) return null
  const [id, n] = [...cnt.entries()]
    .sort((a, b) => b[1] - a[1] || weight.get(b[0]) - weight.get(a[0]))[0]
  return { competency: competencies.find(c => c.id === id), postingCount: n }
}
