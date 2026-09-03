import { DATA } from './data.js'
import { delay } from './_delay.js'

/* 목 AI. 추출은 첫 공고의 required 를 돌려주고, 초안은 문항에 박힌 aiDraft 를 준다.
 * 실제 모델은 dev 에서 vite 플러그인이, 배포에서 Spring 이 서빙한다 (api/real/ai.js). */

export async function extract() {
  await delay('ai')
  const p = DATA.postings[0]
  return { required: p.required, newCompetencies: [], role: p.role }
}

export async function draft(questionId) {
  await delay('ai')
  const q = DATA.questions.find(q => q.id === questionId)
  if (!q?.aiDraft) throw new Error('이 문항은 아직 AI 초안이 없습니다')
  return { draft: q.aiDraft }
}
