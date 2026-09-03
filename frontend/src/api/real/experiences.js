/* 내 경험 — 백엔드 ExperienceController (docs/api-spec-v6.md §4).
 *
 *   GET /api/experiences                 [{ id, title, category, startDate, endDate, situation, task, action, result,
 *                                          aiTaskId, competencies:[{ competencyId, name, strength }], usedInQuestions }]
 *   POST /api/experiences                201 { experience, reassess:{ postingCount, taskIds } }
 *   PUT  /api/experiences/{id}           200 같은 모양
 *
 * 인테이크(POST /api/experience-intakes)는 api/real/ai.js 다 — 202 + 폴링이라 결이 다르다. */

import { client } from '../client.js'

export const list = () => client.get('/experiences')

/** 본문은 ExperienceDialog·IntakePanel 이 v6 모양 그대로 만든다. intakeTaskId 는 인테이크에서 올 때만 있다. */
export const create = body => client.post('/experiences', body)

/** PUT 본문에는 intakeTaskId 가 없다 — 출처는 수정으로 안 바뀐다. 들어와도 서버가 무시하지만 보내지 않는다. */
export const update = (id, { intakeTaskId, ...body }) => client.put(`/experiences/${id}`, body)
