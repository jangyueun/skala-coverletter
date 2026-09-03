/* 자소서 분량 계산.
 *
 * 문장 점검(감점 항목 실시간 지적)은 화면에서 뺐다. 쓰는 도중에 경고가
 * 계속 뜨면 글이 안 써진다 — 고치는 건 다 쓰고 나서 할 일이다.
 * 남은 건 분량 하나이고, 이건 쓰면서 알아야 하는 정보다. */

/** 분량 상태 — 막대와 숫자가 같은 판정을 쓰게 한다. */
export function lengthState(text, question) {
  const n = (text || '').trim().length
  const limit = question?.charLimit ?? 0
  if (!limit) return { n, limit, pct: 0, tone: 'idle' }
  const pct = Math.min(100, (n / limit) * 100)
  const tone = n === 0 ? 'idle' : n > limit ? 'bad' : n < limit * 0.8 ? 'bad' : 'ok'
  return { n, limit, pct, tone }
}
