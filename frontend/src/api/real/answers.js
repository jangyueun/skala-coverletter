/* 자소서 문항·답변 — 백엔드 JobPostingController.findQuestions · CoverLetterAnswerController (docs/api-spec-v6.md §3·§5).
 *
 *   GET /api/postings/{postingId}/questions   [{ id, sequence, promptText, lengthLimit, answer|null }]
 *   PUT /api/questions/{questionId}/answer    { questionId, content, charCount, usedExperienceIds, aiTaskId, updatedAt }
 *
 * 전 공고의 문항을 한 번에 주는 API 는 없다. 그래서 list() 는 빈 배열이고 문항은 공고를 열 때
 * questions(postingId) 로 받는다. 카드의 "자소서 작성 중 1/4" 은 목록 DTO 의 essay 요약(서버 계산)이 맡는다. */

import { client } from '../client.js'

/** 목은 전부를 한 번에 주지만 실제는 줄 수 없다 — 스토어가 빈 목록으로 시작해 공고별로 채운다. */
export const list = async () => []

/** 응답에 postingId 가 없다 — 스토어가 붙인다(질문이 어느 공고 것인지는 부른 쪽이 안다). */
export const questions = postingId => client.get(`/postings/${postingId}/questions`)

/** 본문·근거 경험·초안 출처를 한 번에. 글자 수는 서버가 센다. */
export const save = (questionId, { content, usedExperienceIds, draftTaskId = null }) =>
  client.put(`/questions/${questionId}/answer`, { content, usedExperienceIds, draftTaskId })
