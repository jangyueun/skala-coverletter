import { DATA } from './data.js'
import { delay, clone } from './_delay.js'

/* 문항 사본. 모양은 v6 GET /api/postings/{id}/questions 항목 + postingId.
   실제 API 는 공고별로 주지만, 화면이 자소서 진행과 "N개 문항에 사용" 을 전 공고에 걸쳐 세므로
   목은 전부를 한 번에 준다. 백엔드가 붙는 날 questions(postingId) 로 바꾸고 스토어가 공고별로 받는다. */
let questions = clone(DATA.questions)

export async function list() { await delay('answers'); return clone(questions) }

/** PUT /api/questions/{id}/answer — 본문과 근거 경험을 한 번에 저장한다(usedIn() 이 둘을 AND 로 보기 때문).
    글자 수는 서버가 센다. draftTaskId 가 오면 AI 초안을 반영했다는 출처(ai_task_id)로 남는다. */
export async function save(questionId, { content, usedExperienceIds, draftTaskId = null }) {
  await delay('answers')
  const q = questions.find(q => q.id === questionId)
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
