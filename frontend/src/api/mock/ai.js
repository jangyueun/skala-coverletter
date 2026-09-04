import { DATA } from './data.js'
import { delay } from './_delay.js'

/* 목 AI. 실제(api/real/ai.js)는 202 + taskId 를 받고 GET /api/ai-tasks/{id} 를 폴링해 결과를 돌려주는데,
   화면은 그 차이를 모른다 — 둘 다 결과가 담긴 Promise 하나다. 목은 taskId 를 지어낸다.
   공고 분석은 v6 에서 내부 API(/internal/postings/{id}/analysis)라 프론트에 없다. */
let nextTask = 900

/** 초안 — 문항에 미리 박아 둔 글을 준다. 근거 경험은 실제 서버가 쓰고 목은 무시한다. */
export async function draft(questionId) {
  await delay('ai')
  const text = DATA.aiDrafts[questionId]
  if (!text) throw new Error('이 문항은 아직 AI 초안이 없습니다')
  return { taskId: nextTask++, draft: text, charCount: text.length }
}

/** 인테이크 — 링크·파일을 읽는 척하고 고정 후보를 돌려준다. */
export async function intake() {
  await delay('ai')
  return { taskId: nextTask++, candidates: DATA.intakeCandidates }
}
