/* AI. dev 에서는 vite-plugins/aiDevServer.js 가 /api/ai/* 를 서빙한다 (docs/ai-flow.md).
 * 키가 없으면 503 + 이유가 온다 — ApiError 로 던져지고 화면이 ErrorNote 로 그린다.
 * 배포에서는 Spring 이 같은 경로를 서빙한다. 프런트는 안 바뀐다. */

import { client } from '../client.js'

/** 공고 원문 → { required:[{competencyId, weight, evidence}], newCompetencies, role } */
export const extract = text => client.post('/ai/extract', { text })

/**
 * 자소서 초안. 아직 서버에 없다 — 부르면 404 가 ApiError 로 온다.
 * 백엔드가 붙을 때까지는 api/index.js 가 mock 을 쓴다.
 */
export const draft = (questionId, usedExperienceIds) =>
  client.post('/ai/draft', { questionId, usedExperienceIds })
