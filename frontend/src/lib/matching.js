/* 목업 render.js 의 파생 계산을 옮긴 것.
 *
 * 화면이 아니라 계산이므로 컴포넌트에 두지 않는다. 나중에 이 계산이
 * 서버로 옮겨가면 이 파일만 API 호출로 바뀐다. */

import { DATA } from './mockData.js'

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

export const byId = id => DATA.competencies.find(c => c.id === id)
export const postingById = id => DATA.postings.find(p => p.id === id)

/**
 * 공고 하나에 대한 나의 매칭.
 *
 * score = min(1, Σ strength) — 같은 역량을 여러 경험이 뒷받침하면 확신이 오르지만
 * 1을 넘지 않는다. overall 은 가중 평균이라 "무겁게 요구되는 것을 못 채우면 크게 깎인다".
 */
export function computeMatch(posting, experiences = DATA.experiences) {
  const rows = posting.required.map(r => {
    const evid = experiences.filter(e => e.competencyIds.includes(r.competencyId))
    const strength = evid.reduce((a, e) => a + (e.strength?.[r.competencyId] ?? SCORE.DEFAULT_STRENGTH), 0)
    const score = Math.min(1, strength)
    return {
      ...r,
      comp: byId(r.competencyId),
      evid,
      score,
      isGap: evid.length === 0 || score < SCORE.GAP,
    }
  })
  const wsum = rows.reduce((a, r) => a + r.weight, 0)
  const overall = wsum ? rows.reduce((a, r) => a + r.weight * r.score, 0) / wsum : 0
  return { rows, overall }
}

/** 마감까지 남은 날. 음수면 지난 공고다. */
export function dday(deadline) {
  const d = new Date(deadline + 'T23:59:59')
  return Math.ceil((d - new Date()) / 86400000)
}

export const ESSAY_STATE = {
  NO_APP:  { label: '지원서 없음', tone: 'mut' },
  NO_Q:    { label: '문항 미등록', tone: 'mut' },
  EMPTY:   { label: '작성 전',     tone: 'warn' },
  WRITING: { label: '작성 중',     tone: 'warn' },
  DONE:    { label: '작성 완료',   tone: 'ok' },
}

/**
 * 자소서 진행 상태.
 *
 * 제출 여부는 모델에 없다 — ATS 연동이 없어 알 수 없기 때문이다.
 * 대신 draft 가 채워진 문항 수에서 파생한다. 아는 것만 말한다.
 */
export function essayProgress(posting) {
  const app = DATA.applications.find(a => a.postingId === posting.id)
  if (!app) return { state: 'NO_APP', done: 0, total: 0, ...ESSAY_STATE.NO_APP }

  const qs = DATA.questions.filter(q => q.applicationId === app.id)
  if (!qs.length) return { state: 'NO_Q', done: 0, total: 0, ...ESSAY_STATE.NO_Q }

  const done = qs.filter(q => (q.draft || '').trim()).length
  const state = done === 0 ? 'EMPTY' : done < qs.length ? 'WRITING' : 'DONE'
  return { state, done, total: qs.length, ...ESSAY_STATE[state] }
}

/**
 * 이 경험이 실제로 쓰인 곳.
 *
 * 관측 가능한 것만 센다 — 본문이 작성된 답변에 근거로 걸린 것만.
 * 단위는 기업이 아니라 **공고**다. 공고가 직무 단위라 같은 기업의 다른 직무는 따로 센다.
 */
export function usedIn(experienceId) {
  const qs = DATA.questions.filter(q =>
    (q.usedExperienceIds || []).includes(experienceId) && (q.draft || '').trim())
  const postingIds = new Set(qs.map(q => {
    const a = DATA.applications.find(x => x.id === q.applicationId)
    return a ? a.postingId : null
  }).filter(Boolean))
  return { questions: qs.length, postings: postingIds.size }
}

/**
 * 여러 공고에서 동시에 갭인 역량 — "다음에 뭘 채워야 하나".
 *
 * 평균 매칭률을 대신한다. 평균은 행동으로 이어지지 않는 숫자였다.
 * 동점이면 가중치 합이 큰 쪽을 먼저 — 같은 3건이어도 더 무겁게 요구되는 것이 있다.
 */
export function topGap(postings, experiences = DATA.experiences) {
  const cnt = new Map()
  const weight = new Map()
  postings.forEach(p => {
    computeMatch(p, experiences).rows.filter(r => r.isGap).forEach(r => {
      cnt.set(r.competencyId, (cnt.get(r.competencyId) || 0) + 1)
      weight.set(r.competencyId, (weight.get(r.competencyId) || 0) + r.weight)
    })
  })
  if (!cnt.size) return null
  const [id, n] = [...cnt.entries()]
    .sort((a, b) => b[1] - a[1] || weight.get(b[0]) - weight.get(a[0]))[0]
  return { competency: byId(id), postingCount: n }
}

/** 약 / 중 / 강 — 내부값은 연속이지만 사람에게는 3단계로만 보여준다. */
export const STR = [
  { v: 0.4, lab: '약' },
  { v: 0.7, lab: '중' },
  { v: 0.9, lab: '강' },
]
export const strLabel = v => (v >= 0.8 ? '강' : v >= 0.6 ? '중' : '약')
