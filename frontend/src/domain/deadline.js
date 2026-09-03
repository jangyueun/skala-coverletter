/* 마감. 마감은 날짜가 아니라 시각이다 — "오늘까지" 와 "오늘 18시까지" 는
 * 남은 시간이 여섯 시간 다르고, 그 여섯 시간에 자소서를 쓸 수 있느냐가 갈린다.
 *
 * deadline 문자열 형식: 'YYYY-MM-DD HH:mm' */

export function deadlineAt(deadline) {
  /* 'YYYY-MM-DD HH:mm' 과 ISO-8601 을 둘 다 받는다.
     공백 형식에만 ':00' 을 붙이던 옛 코드는 ISO 가 오면 '...T18:00:00:00' 을 만들어
     Invalid Date 가 됐고, isClosed 의 `NaN < now` 가 **false** 라 마감된 공고가
     전부 살아 있는 것으로 잡혔다 — 조용한 fail-open 이다.
     백엔드가 어떤 형식을 줄지 모르므로 여기서 막는다. */
  const s = String(deadline).trim()
  const d = new Date(/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}$/.test(s) ? s.replace(' ', 'T') + ':00' : s)
  if (Number.isNaN(d.getTime())) throw new Error(`마감 형식을 읽을 수 없습니다: ${deadline}`)
  return d
}

/** 며칠 남았나 — 표시용. 남은 시간을 올림하므로 오늘 18시 마감도 D-0 이 아니라 D-1 이다. */
export function dday(deadline, now = new Date()) {
  return Math.ceil((deadlineAt(deadline) - now) / 86400000)
}

/* 마감 여부는 dday 로 보지 않는다. 오늘 18시 마감을 저녁 8시에 보면
 * 남은 시간이 -0.08일이고 Math.ceil 은 -0 을 준다 — d >= 0 이 참이 되어
 * 이미 끝난 공고가 살아 있는 것으로 잡힌다. 시각을 직접 비교한다. */
export function isClosed(deadline, now = new Date()) {
  return deadlineAt(deadline) < now
}
