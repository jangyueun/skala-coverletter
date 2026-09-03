/* 역량 사전의 범주. 순수 상수와 순수 함수.
 *
 * "무엇을 할 수 있나(ROLE) / 무엇으로 하나(TECH) / 어떻게 일하나(SOFT) /
 *  어느 산업인가(DOMAIN) / 어떤 사람인가(VALUE)" — 공고 219건에서 뽑은 축이다.
 *
 * label 은 넉넉한 자리(사이드바, 선택 목록), short 는 좁은 자리(표 한 칸)에 쓴다.
 * 이 맵이 화면마다 복사돼 있으면 범주를 하나 고칠 때 한쪽만 바뀐다. */

export const CATEGORIES = [
  { k: 'ROLE',   label: '직무 역량',   short: '직무' },
  { k: 'TECH',   label: '기술·언어',   short: '기술' },
  { k: 'SOFT',   label: '일하는 방식', short: '협업' },
  { k: 'DOMAIN', label: '산업',       short: '산업' },
  { k: 'VALUE',  label: '인재상',     short: '인재상' },
]

export const catShort = k => CATEGORIES.find(c => c.k === k)?.short ?? k

/** 역량 목록을 범주 순서대로 묶는다. 빈 범주는 떨군다 — 빈 줄은 정보가 없다. */
export function groupByCategory(list) {
  return CATEGORIES
    .map(c => ({ ...c, items: list.filter(x => x.category === c.k) }))
    .filter(g => g.items.length)
}
