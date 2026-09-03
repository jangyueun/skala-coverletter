/* 자소서 계산. 순수 함수 — 데이터는 전부 인자로 받는다. */

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
export function essayProgress(posting, applications, questions) {
  const app = applications.find(a => a.postingId === posting.id)
  if (!app) return { state: 'NO_APP', done: 0, total: 0, ...ESSAY_STATE.NO_APP }

  const qs = questions.filter(q => q.applicationId === app.id)
  if (!qs.length) return { state: 'NO_Q', done: 0, total: 0, ...ESSAY_STATE.NO_Q }

  const done = qs.filter(q => (q.draft || '').trim()).length
  const state = done === 0 ? 'EMPTY' : done < qs.length ? 'WRITING' : 'DONE'
  return { state, done, total: qs.length, ...ESSAY_STATE[state] }
}

/**
 * 이 경험이 실제로 쓰인 곳.
 *
 * 관측 가능한 것만 센다 — 본문이 작성된 답변에 근거로 걸린 것만.
 * 본문과 근거 경험을 따로 저장하면 이 AND 조건 때문에 배지가 반쪽 상태에서 깜빡인다.
 * 그래서 answers 스토어는 둘을 한 번에 커밋한다.
 */
export function usedIn(experienceId, applications, questions) {
  const qs = questions.filter(q =>
    (q.usedExperienceIds || []).includes(experienceId) && (q.draft || '').trim())
  const postingIds = new Set(qs.map(q => {
    const a = applications.find(x => x.id === q.applicationId)
    return a ? a.postingId : null
  }).filter(Boolean))
  return { questions: qs.length, postings: postingIds.size }
}

/**
 * 분량 상태 — 막대와 숫자가 같은 판정을 쓰게 한다.
 *
 * 문장 점검(감점 항목 실시간 지적)은 화면에서 뺐다. 쓰는 도중에 경고가
 * 계속 뜨면 글이 안 써진다. 남은 건 분량 하나이고, 이건 쓰면서 알아야 한다.
 */
export function lengthState(text, question) {
  const n = (text || '').trim().length
  const limit = question?.charLimit ?? 0
  if (!limit) return { n, limit, pct: 0, tone: 'idle' }
  const pct = Math.min(100, (n / limit) * 100)
  const tone = n === 0 ? 'idle' : n > limit ? 'bad' : n < limit * 0.8 ? 'bad' : 'ok'
  return { n, limit, pct, tone }
}
