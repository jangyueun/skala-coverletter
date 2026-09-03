import { DATA } from './data.js'
import { delay, clone } from './_delay.js'

/* 세션 안에서만 사는 사본. 등록·수정은 여기에 쓰고, 다시 list() 하면 바뀐 게 보인다.
   모양은 v6 GET /api/experiences 항목이다. usedInQuestions(답변에 근거로 쓰인 문항 수)는
   서버가 세어 주는 값인데, 화면은 답변 스토어에서 바로 세므로(derived.usedIn) 목은 싣지 않는다. */
let rows = clone(DATA.experiences)

const nameOf = id => DATA.competencies.find(c => c.id === id)?.name ?? null
const decorate = e => ({ ...e, competencies: e.competencies.map(c => ({ ...c, name: nameOf(c.competencyId) })) })

export async function list() { await delay('experiences'); return clone(rows.map(decorate)) }

/** 저장 응답은 { experience, reassess } — 경험이 바뀌면 활성 공고마다 MATCH 작업이 잡힌다(v6 4절).
    목은 매칭을 브라우저가 계산하므로 taskIds 를 비운다. */
function reassess() {
  const now = new Date()
  return { postingCount: DATA.postings.filter(p => new Date(p.deadline) > now).length, taskIds: [] }
}

/** v6 POST 본문 → 행. intakeTaskId 는 인테이크 후보에서 등록할 때만 오고, 저장되는 건 aiTaskId 다. */
function toRow({ intakeTaskId, ...body }, id, prev) {
  return { ...prev, ...clone(body), id, aiTaskId: intakeTaskId ?? prev?.aiTaskId ?? null }
}

export async function create(body) {
  await delay('experiences')
  const id = Math.max(0, ...rows.map(e => e.id)) + 1
  const row = toRow(body, id)
  rows.push(row)
  return { experience: clone(decorate(row)), reassess: reassess() }
}

export async function update(id, body) {
  await delay('experiences')
  const i = rows.findIndex(e => e.id === id)
  if (i < 0) throw new Error(`experience ${id} 없음`)
  rows[i] = toRow(body, id, rows[i])
  return { experience: clone(decorate(rows[i])), reassess: reassess() }
}
