/* 경험의 분류와 기간. 순수 상수와 순수 함수.
 *
 * v6 부터 서버는 분류를 코드값(TEAM_PROJECT …)으로, 기간을 startDate·endDate(YYYY-MM-DD, 둘 다 NULL 허용)로 준다.
 * 한글 라벨과 "2025.03 – 2025.11" 같은 표시 문자열은 프론트가 만든다 — 이 파일이 그 한 곳이다.
 * 화면마다 따로 만들면 카드와 자소서 옆줄이 다른 형식을 쓰게 된다. */

export const EXPERIENCE_CATEGORIES = [
  { k: 'TEAM_PROJECT',      label: '팀 프로젝트' },
  { k: 'PERSONAL_PROJECT',  label: '개인 프로젝트' },
  { k: 'PRACTICE_PROJECT',  label: '실습 프로젝트' },
  { k: 'EXTERNAL_ACTIVITY', label: '대외활동' },
  { k: 'EMPLOYMENT',        label: '인턴·근무' },
  { k: 'AWARD_CERTIFICATE', label: '수상·자격' },
]

export const categoryLabel = k => EXPERIENCE_CATEGORIES.find(c => c.k === k)?.label ?? k

/** 'YYYY-MM-DD' → 'YYYY.MM'. 월 입력은 1일로 저장되므로 일은 버린다. */
const ym = d => String(d).slice(0, 7).replace('-', '.')

/**
 * 기간 표시 문자열.
 *   둘 다 없음   → '기간 미입력'
 *   시작만       → '2026.08'   진행 중이거나 한 달짜리 — 서버는 둘을 구분하지 않는다
 *   같은 달      → '2026.08'
 *   시작 · 종료  → '2025.03 – 2025.11'
 *   종료만       → '– 2025.11'  서버 CHECK 가 막지 않는 조합이라 그리긴 한다
 */
export function periodLabel(startDate, endDate) {
  const s = startDate ? ym(startDate) : null
  const e = endDate ? ym(endDate) : null
  if (!s && !e) return '기간 미입력'
  if (!e || s === e) return s
  if (!s) return `– ${e}`
  return `${s} – ${e}`
}

/** `<input type="month">` 값('YYYY-MM') ↔ 서버 날짜('YYYY-MM-01') */
export const toMonth = d => (d ? String(d).slice(0, 7) : '')
export const fromMonth = m => (m ? `${m}-01` : null)

/** 서버 CHECK(end_date >= start_date)와 같은 판정. 둘 중 하나라도 없으면 통과. ISO 날짜라 문자열 비교면 된다. */
export const periodValid = (startDate, endDate) => !startDate || !endDate || endDate >= startDate
