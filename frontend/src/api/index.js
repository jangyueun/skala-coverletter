/* 서버와 말하는 유일한 층의 입구. 스토어는 여기서만 import 한다.
 *
 * **기본은 전부 mock 이다.** 백엔드(:8080)는 Supabase 접속 정보(.env)가 있어야 뜨고, 그게 없는 컴에서
 * real 을 켜면 `npm run dev` 만 한 사람이 이유도 모른 채 401·연결 실패 화면을 본다.
 *
 * 백엔드를 띄운 사람만 VITE_API_MOCK=0 으로 real 을 켠다. 그때 —
 *   auth · postings · experiences · answers → real (vite 프록시가 /api 를 :8080 으로 넘긴다)
 *   ai                                     → real 이지만 dev 는 vite 플러그인(aiDevServer.js)이 서빙한다.
 *                                            Spring 에 GET /api/ai-tasks 와 MATCH·DRAFT·INTAKE 워커가 아직 없어서다.
 *                                            그게 생기면 vite.config.js 에서 aiDevServer() 한 줄만 지운다.
 *
 * 목과 real 의 반환 모양은 같다(docs/api-spec-v6.md DTO). 실제 API 가 목과 다른 두 곳은 real 쪽이 맞춘다 —
 *   postings.list  목록 DTO 에 상세를 합친다(api/real/postings.js 주석)
 *   answers.list   빈 배열. 문항은 공고를 열 때 questions(postingId) 로 받는다(stores/answers.js loadFor) */

import * as realAuth from './real/auth.js'
import * as realPostings from './real/postings.js'
import * as realExperiences from './real/experiences.js'
import * as realAnswers from './real/answers.js'
import * as realAi from './real/ai.js'
import * as mockAuth from './mock/auth.js'
import * as mockPostings from './mock/postings.js'
import * as mockExperiences from './mock/experiences.js'
import * as mockAnswers from './mock/answers.js'
import * as mockAi from './mock/ai.js'

const REAL = import.meta.env.VITE_API_MOCK === '0'

export const api = {
  auth:        REAL ? realAuth : mockAuth,
  postings:    REAL ? realPostings : mockPostings,
  experiences: REAL ? realExperiences : mockExperiences,
  answers:     REAL ? realAnswers : mockAnswers,
  // 키가 없으면 503 이 오고, 그건 화면이 오류 문구로 그린다.
  ai:          REAL ? realAi : mockAi,
}
