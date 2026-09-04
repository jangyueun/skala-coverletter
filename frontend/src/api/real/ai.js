/* AI 는 비동기 작업이다(v6 6절). 요청은 202 + { taskId } 로 끝나고, 결과는
 * GET /api/ai-tasks/{taskId} 를 폴링해 받는다. 화면은 이걸 모른다 — 이 파일이 폴링을 안고
 * 결과가 담긴 Promise 하나를 돌려준다. 목(api/mock/ai.js)과 시그니처가 같다.
 *
 * 같은 입력으로 다시 부르면 서버가 200 + 기존 taskId 를 준다 — 그것도 그냥 폴링하면 된다.
 * 같은 대상에 다른 입력이 진행 중이면 409 *_ALREADY_RUNNING 이 ApiError 로 온다.
 * dev 도 배포도 Spring 이 서빙한다(워커가 AI 서버를 부른다). 초안은 몇십 초, 인테이크는 자료를 읽느라 몇 분이 걸릴 수 있다. */

import { client, ApiError } from '../client.js'

export const task = taskId => client.get(`/ai-tasks/${taskId}`)

/** COMPLETED 면 작업을, FAILED 면 error 를 ApiError 로 던진다. 1초 간격, 최대 10분 — 인테이크가 자료를 읽는 시간까지 본다. */
export async function waitFor(taskId, { interval = 1000, timeout = 10 * 60 * 1000 } = {}) {
  const until = Date.now() + timeout
  for (;;) {
    const t = await task(taskId)
    if (t.status === 'COMPLETED') return t
    if (t.status === 'FAILED') throw new ApiError(502, t.error?.message || 'AI 작업이 실패했습니다', t.error)
    if (Date.now() > until) throw new ApiError(504, 'AI 작업이 너무 오래 걸립니다. 잠시 후 다시 시도하세요', { taskId })
    await new Promise(r => setTimeout(r, interval))
  }
}

/**
 * 자소서 초안 — POST /api/questions/{id}/drafts { experienceIds } → result { draft, charCount }
 * 초안은 서버에 저장되지 않는다. 화면이 버퍼에 넣고, 저장할 때 draftTaskId 로 출처를 남긴다.
 */
export async function draft(questionId, experienceIds) {
  const { taskId } = await client.post(`/questions/${questionId}/drafts`, { experienceIds })
  const { result } = await waitFor(taskId)
  return { taskId, ...result }
}

/**
 * 포폴 인테이크 — POST /api/experience-intakes (multipart) → result { candidates }
 * links 는 줄바꿈으로 이은 텍스트 한 칸, files 는 PDF·MD·TXT (파일당 10MB · 최대 5개, 반복 필드).
 * 서버가 파일을 Storage 에 올리고 AI 서버에는 URL 만 넘긴다 — 프런트는 base64 를 만들지 않는다.
 */
export async function intake(links, files) {
  const form = new FormData()
  form.append('links', links.join('\n'))
  for (const f of files) form.append('files', f, f.name)
  const { taskId } = await client.post('/experience-intakes', form)
  const { result } = await waitFor(taskId)
  return { taskId, ...result }
}
