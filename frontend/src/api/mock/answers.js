import { DATA } from './data.js'
import { delay, clone } from './_delay.js'

let questions = clone(DATA.questions)

export async function applications() { await delay('answers'); return clone(DATA.applications) }
export async function list()         { await delay('answers'); return clone(questions) }

/** 본문과 근거 경험을 한 번에 저장한다 — usedIn() 이 둘을 AND 로 보기 때문이다. */
export async function save(questionId, { draft, usedExperienceIds }) {
  await delay('answers')
  const i = questions.findIndex(q => q.id === questionId)
  if (i < 0) throw new Error(`question ${questionId} 없음`)
  questions[i] = { ...questions[i], draft, usedExperienceIds: [...usedExperienceIds] }
  return clone(questions[i])
}
