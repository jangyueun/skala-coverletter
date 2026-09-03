/* 서버와 말하는 유일한 층의 입구. 스토어는 여기서만 import 한다.
 *
 * **기본은 전부 mock 이다.** 지금 백엔드에 실제로 있는 건 인증뿐이고 그마저
 * 팀원 대부분의 컴에는 안 떠 있다. 기본을 real 로 두면 `npm run dev` 만 한 사람이
 * 이유도 모른 채 로그아웃 화면을 본다 — 프록시가 :8080 을 못 찾아 me() 가 죽기 때문이다.
 *
 * 백엔드를 띄운 사람만 VITE_API_MOCK=0 으로 real 을 켠다. 그때 —
 *   auth   → real   (vite 프록시가 :8080 으로 넘긴다)
 *   ai     → real   (dev 는 vite 플러그인, 배포는 Spring)
 *   나머지 → mock   (팀원이 API 를 만드는 중이다)
 *
 * 목의 반환 모양은 docs/api-spec-v6.md 의 DTO 다. 팀원이 /api/postings 를 올리면 여기 한 줄만 바꾼다:
 *   postings: mockPostings  →  REAL ? realPostings : mockPostings
 * 스토어·화면은 안 건드린다. 그게 이 층을 두는 이유다. */

import * as realAuth from './real/auth.js'
import * as mockAuth from './mock/auth.js'
import * as mockPostings from './mock/postings.js'
import * as mockExperiences from './mock/experiences.js'
import * as mockAnswers from './mock/answers.js'
import * as mockAi from './mock/ai.js'
import * as realAi from './real/ai.js'

const REAL = import.meta.env.VITE_API_MOCK === '0'

export const api = {
  auth:        REAL ? realAuth : mockAuth,
  postings:    mockPostings,      // TODO GET /api/postings · /api/competencies 가 생기면 real 로
  experiences: mockExperiences,   // TODO GET·POST /api/experiences · PUT /api/experiences/{id}
  answers:     mockAnswers,       // TODO GET /api/postings/{id}/questions · PUT /api/questions/{id}/answer
  // AI 는 dev 에서 vite 플러그인이 /api/experience-intakes · /api/ai-tasks 를 서빙하므로 real 이 그대로 된다.
  // 키가 없으면 503 이 오고, 그건 화면이 오류 문구로 그린다.
  ai:          REAL ? realAi : mockAi,
}
