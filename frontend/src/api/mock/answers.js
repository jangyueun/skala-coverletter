import { DATA } from './data.js'
import { delay, clone } from './_delay.js'

/* 문항 사본. 모양은 v6 GET /api/postings/{id}/questions 항목 + postingId.
   실제 API 는 공고별로만 주고 전부를 한 번에 주는 길이 없다(api/real/answers.js 의 list 는 빈 배열).
   목은 list() 로 전부를 한 번에 주고, questions(postingId) 도 같은 사본에서 잘라 준다 —
   그래야 상세 화면이 두 모드에서 같은 경로(loadFor)를 탄다. */
let rows = clone(DATA.questions)

export async function list() { await delay('answers'); return clone(rows) }

/** GET /api/postings/{postingId}/questions — 순번대로. 없는 공고면 빈 배열(실제는 404 지만 화면이 갈 일이 없다). */
export async function questions(postingId) {
  await delay('answers')
  return clone(rows.filter(q => q.postingId === Number(postingId)).sort((a, b) => a.sequence - b.sequence))
}

/** PUT /api/questions/{id}/answer — 본문과 근거 경험을 한 번에 저장한다(usedIn() 이 둘을 AND 로 보기 때문).
    글자 수는 서버가 센다. draftTaskId 가 오면 AI 초안을 반영했다는 출처(ai_task_id)로 남는다. */
export async function save(questionId, { content, usedExperienceIds, draftTaskId = null }) {
  await delay('answers')
  const q = rows.find(q => q.id === questionId)
  if (!q) throw new Error(`question ${questionId} 없음`)
  q.answer = {
    content,
    charCount: content.trim().length,
    usedExperienceIds: [...usedExperienceIds],
    aiTaskId: draftTaskId ?? q.answer?.aiTaskId ?? null,
    updatedAt: new Date().toISOString(),
  }
  return clone({ questionId, ...q.answer })
}
