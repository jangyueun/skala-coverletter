import { DATA } from './data.js'
import { delay, clone } from './_delay.js'

export async function list()       { await delay('postings'); return clone(DATA.postings) }
export async function dictionary() { await delay('postings'); return clone(DATA.competencies) }
