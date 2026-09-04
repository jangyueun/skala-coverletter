/* 서버와 말하는 유일한 층의 입구. 스토어는 여기서만 import 한다.
 *
 * **기본은 전부 mock 이다.** 백엔드(:8080)는 Supabase 접속 정보(.env)가 있어야 뜨고, 그게 없는 컴에서
 * real 을 켜면 `npm run dev` 만 한 사람이 이유도 모른 채 401·연결 실패 화면을 본다.
 *
 * 백엔드를 띄운 사람만 VITE_API_MOCK=0 으로 real 을 켠다. 그때 다섯 자원 전부 real 이다 —
 * vite 프록시가 /api 를 :8080 으로 넘기고, AI(인테이크·초안)도 Spring 이 받아 워커가 AI 서버(ai/, :8000)를 부른다.
 * 그래서 real 모드로 AI 를 쓰려면 Spring 과 AI 서버가 같이 떠 있어야 한다(docs/dev-environment.md).
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
