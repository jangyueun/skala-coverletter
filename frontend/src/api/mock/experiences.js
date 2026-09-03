import { DATA } from './data.js'
import { delay, clone } from './_delay.js'

/* 세션 안에서만 사는 사본. 등록·수정은 여기에 쓰고, 다시 list() 하면 바뀐 게 보인다. */
let rows = clone(DATA.experiences)

export async function list() { await delay('experiences'); return clone(rows) }

export async function create(exp) {
  await delay('experiences')
  const id = Math.max(0, ...rows.map(e => e.id)) + 1
  const row = { ...clone(exp), id }
  rows.push(row)
  return clone(row)
}

export async function update(id, patch) {
  await delay('experiences')
  const i = rows.findIndex(e => e.id === id)
  if (i < 0) throw new Error(`experience ${id} 없음`)
  rows[i] = { ...rows[i], ...clone(patch) }
  return clone(rows[i])
}

