/* AI. dev 에서는 vite-plugins/aiDevServer.js 가 /api/ai/* 를 서빙한다.
 * 키가 없으면 503 + 이유가 온다 — ApiError 로 던져지고 화면이 ErrorNote 로 그린다.
 * 배포에서는 Spring 이 같은 경로를 서빙한다. 프런트는 안 바뀐다. */

import { client } from '../client.js'

/**
 * 공고 원문 → { required:[{competencyId, weight, evidence}], newCompetencies, role }
 *
 * 사전을 같이 보낸다. 서버는 이 안에서만 고르게 하고, 밖의 id 가 오면 버린다 —
 * 지어낸 id 가 매칭 점수로 흘러들면 안 되기 때문이다. 아직 이 함수를 부르는 화면은 없다
 * (공고 등록이 백엔드 몫이라). 붙일 때 사전은 postings 스토어의 competencies 를 준다. */
export const extract = (text, competencies) =>
  client.post('/ai/extract', { text, competencies })

/**
 * 자소서 초안. 아직 서버에 없다 — 부르면 404 가 ApiError 로 온다.
 * 백엔드가 붙을 때까지는 api/index.js 가 mock 을 쓴다.
 */
export const draft = (questionId, usedExperienceIds) =>
  client.post('/ai/draft', { questionId, usedExperienceIds })

/**
 * 포폴 인테이크 — 링크는 모델이 web_fetch 로 직접 읽는다.
 *
 * files 는 base64 로 싣는다. PDF 는 모델이 네이티브로 읽고, md·txt 는 서버가
 * 풀어서 본문에 붙인다. 업로드 스토리지가 없으므로 요청 본문에 그대로 담는다 —
 * Anthropic 요청 한도가 32MB 라 발표자료 몇 건은 문제없다.
 */
export const intake = (links, files, competencies) =>
  client.post('/ai/intake', { links, files, competencies })
