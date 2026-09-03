/* 자소서 계산. 순수 함수 — 데이터는 전부 인자로 받는다.
 *
 * 문항 모양은 v6 GET /api/postings/{id}/questions 항목이다:
 *   { id, postingId, sequence, promptText, lengthLimit, answer: { content, charCount, usedExperienceIds, updatedAt } | null }
 * 지원서(application)는 없어졌다 — 문항이 공고에 직접 붙는다. */

export const ESSAY_STATE = {
  NO_QUESTIONS: { label: '문항 없음', tone: 'mut' },
  EMPTY:        { label: '작성 전',   tone: 'warn' },
  WRITING:      { label: '작성 중',   tone: 'warn' },
  DONE:         { label: '작성 완료', tone: 'ok' },
}

/** 답변 본문. 서버는 답변이 없으면 null 을 준다 — 빈 문자열로 본다. */
export const answerText = q => (q?.answer?.content ?? '').trim()

/**
 * 자소서 진행 상태. 목록 DTO 의 essay.state 와 같은 네 값이다.
 *
 * 제출 여부는 모델에 없다 — ATS 연동이 없어 알 수 없기 때문이다.
 * 대신 본문이 채워진 문항 수에서 파생한다. 아는 것만 말한다.
 *
 * @param {object[]} questions  이 공고의 문항들 (이미 걸러진 것)
 */
export function essayProgress(questions) {
  if (!questions.length) return { state: 'NO_QUESTIONS', done: 0, total: 0, ...ESSAY_STATE.NO_QUESTIONS }
  const done = questions.filter(q => answerText(q)).length
  const state = done === 0 ? 'EMPTY' : done < questions.length ? 'WRITING' : 'DONE'
  return { state, done, total: questions.length, ...ESSAY_STATE[state] }
}

/**
 * 목록 DTO 의 essay 요약(서버 계산) → essayProgress 와 같은 모양.
 *   { state: 'WRITING', answered: 1, total: 4 }  →  { state, done: 1, total: 4, label, tone }
 * 문항을 브라우저에 다 들고 있지 않은 실제 서버 모드에서 카드가 이걸 쓴다. 모르는 state 는 NO_QUESTIONS 로 본다.
 */
export function essayFromSummary(summary) {
  const state = ESSAY_STATE[summary?.state] ? summary.state : 'NO_QUESTIONS'
  return { state, done: summary?.answered ?? 0, total: summary?.total ?? 0, ...ESSAY_STATE[state] }
}

/**
 * 이 경험이 실제로 쓰인 곳.
 *
 * 관측 가능한 것만 센다 — 본문이 작성된 답변에 근거로 걸린 것만.
 * 본문과 근거 경험을 따로 저장하면 이 AND 조건 때문에 배지가 반쪽 상태에서 깜빡인다.
 * 그래서 answers 스토어는 둘을 한 번에 커밋한다.
 * (서버도 같은 값을 usedInQuestions 로 주지만, 저장 직후에도 맞아야 해서 여기서 다시 센다.)
 */
export function usedIn(experienceId, questions) {
  const qs = questions.filter(q =>
    (q.answer?.usedExperienceIds || []).includes(experienceId) && answerText(q))
  return { questions: qs.length, postings: new Set(qs.map(q => q.postingId)).size }
}

/**
 * 분량 상태 — 막대와 숫자가 같은 판정을 쓰게 한다.
 *
 * 문장 점검(감점 항목 실시간 지적)은 화면에서 뺐다. 쓰는 도중에 경고가
 * 계속 뜨면 글이 안 써진다. 남은 건 분량 하나이고, 이건 쓰면서 알아야 한다.
 * lengthLimit 이 null 이면 제한 없음이다.
 */
export function lengthState(text, question) {
  const n = (text || '').trim().length
  const limit = question?.lengthLimit ?? 0
  if (!limit) return { n, limit, pct: 0, tone: 'idle' }
  const pct = Math.min(100, (n / limit) * 100)
  const tone = n === 0 ? 'idle' : n > limit ? 'bad' : n < limit * 0.8 ? 'bad' : 'ok'
  return { n, limit, pct, tone }
}
