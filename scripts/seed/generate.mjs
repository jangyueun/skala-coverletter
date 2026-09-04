/* frontend/src/api/mock/data.js 에서 개발용 시드 SQL 두 개를 만든다. 이 파일을 고치지 말고 data.js 를 고친 뒤 다시 돌린다.
 *
 *   node scripts/seed/generate.mjs
 *     → scripts/seed/competencies.sql     역량 사전 51개 + 별칭 (프론트 사전과 같은 것). 이미 있는 이름은 건너뛴다
 *     → scripts/seed/my-experiences.sql   내 샘플 경험 6건. 이메일로 사용자를 찾고, 같은 제목이 있으면 건너뛴다
 *
 * 왜 SQL 인가 — POST /api/experiences 는 Slack 세션이 있어야 해서 스크립트가 못 부른다. DB 에 직접 넣는 게 제일 짧다.
 * Flyway 마이그레이션에 두지 않는 이유 — 팀 공용 Supabase 에 남의 개인 경험이 들어가면 안 된다. 이건 각자 로컬용이다.
 *
 * V4 시드의 8개와 프론트 사전은 이름 셋이 다르다. 사전에 둘 다 넣으면 "책임감·오너십" 과 "주도성·오너십" 이 나란히 서므로
 * 프론트 이름을 V4 이름으로 대응시킨다(별칭은 V4 행에 붙는다). 경험 태그도 같은 대응을 탄다. */

import { writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { DATA } from '../../frontend/src/api/mock/data.js'

const here = dirname(fileURLToPath(import.meta.url))

/** 프론트 사전 이름 → V4 시드 이름 */
const RENAME = {
  '타 직군 협업': '협업·커뮤니케이션',
  '주도성·오너십': '책임감·오너십',
  '금융·핀테크': '핀테크·결제 도메인',
}
const nameOf = c => RENAME[c.name] ?? c.name
const q = s => (s == null ? 'null' : `'${String(s).replace(/'/g, "''")}'`)

/* ── competencies.sql ─────────────────────────────────────────── */
const comps = DATA.competencies
const compRows = comps
  .filter(c => !RENAME[c.name])
  .map(c => `    (${q(c.name)}, ${q(c.category)}, now(), now())`)
const aliasRows = comps.flatMap(c => c.aliases.map(a => `    (${q(nameOf(c))}, ${q(a)})`))

writeFileSync(resolve(here, 'competencies.sql'), `-- 역량 사전 — frontend/src/api/mock/data.js 의 competencies(실제 공고 219건에서 도출한 ${comps.length}개)와 별칭.
-- 생성: node scripts/seed/generate.mjs (손으로 고치지 말 것)
-- 사용법:
--   docker compose -f compose.yaml -f compose.localdb.yaml exec -T db psql -U postgres < scripts/seed/competencies.sql
-- 여러 번 돌려도 안전하다 — 이미 있는 이름·별칭은 건너뛴다. V4 시드의 8개는 그대로 두고 프론트 이름 셋을 거기에 대응시켰다:
--   ${Object.entries(RENAME).map(([a, b]) => `${a} → ${b}`).join(' · ')}
\\set ON_ERROR_STOP on
begin;

insert into competencies (name, category, created_at, updated_at)
values
${compRows.join(',\n')}
on conflict (name) do nothing;

-- 별칭은 전역 유일(uk_competency_aliases_alias). 다른 역량이 이미 쓰는 별칭은 건너뛴다.
insert into competency_aliases (competency_id, alias, created_at)
select c.id, a.alias, now()
from (values
${aliasRows.join(',\n')}
) as a(name, alias)
join competencies c on c.name = a.name
on conflict (alias) do nothing;

commit;
`)

/* ── my-experiences.sql ───────────────────────────────────────── */
const byId = new Map(comps.map(c => [c.id, c]))
const exps = DATA.experiences
const expRows = exps.map(e =>
  `    (${[e.title, e.category, e.startDate, e.endDate, e.situation, e.task, e.action, e.result].map(q).join(', ')})`)
const tagRows = exps.flatMap(e => e.competencies.map(t => {
  const c = byId.get(t.competencyId)
  if (!c) throw new Error(`경험 "${e.title}" 의 역량 ${t.competencyId} 이 사전에 없다`)
  return `    (${q(e.title)}, ${q(nameOf(c))}, ${t.strength})`
}))

writeFileSync(resolve(here, 'my-experiences.sql'), `-- 내 샘플 경험 ${exps.length}건 — frontend/src/api/mock/data.js 의 experiences 와 같은 것.
-- 생성: node scripts/seed/generate.mjs (손으로 고치지 말 것)
-- 사용법: Slack 로그인을 한 번 해서 users 에 내 행이 생긴 뒤, 그 이메일로 —
--   docker compose -f compose.yaml -f compose.localdb.yaml exec -T db psql -U postgres -v email=simonjiho@gmail.com < scripts/seed/my-experiences.sql
-- 먼저 competencies.sql 을 넣어야 태그가 다 붙는다(사전에 없는 이름은 태그만 조용히 빠진다).
-- 여러 번 돌려도 안전하다 — 같은 제목이 이미 있으면 건너뛴다. 이메일이 없으면 아무것도 넣지 않는다.
\\set ON_ERROR_STOP on
begin;

with me as (
    select id from users where email = :'email'
),
rows(title, category, start_date, end_date, situation, task, action, result) as (values
${expRows.join(',\n')}
),
ins as (
    insert into experiences (user_id, title, category, start_date, end_date, situation, task, action, result,
                             created_at, updated_at)
    select me.id, r.title, r.category, r.start_date::date, r.end_date::date, r.situation, r.task, r.action, r.result,
           now(), now()
    from me, rows r
    where not exists (select 1 from experiences e where e.user_id = me.id and e.title = r.title)
    returning id, title
),
tags(title, competency, strength) as (values
${tagRows.join(',\n')}
)
insert into experience_competencies (experience_id, competency_id, strength, created_at)
select ins.id, c.id, t.strength, now()
from ins
join tags t on t.title = ins.title
join competencies c on c.name = t.competency;

commit;
`)

console.log(`competencies.sql: ${compRows.length} 역량 + ${aliasRows.length} 별칭 · my-experiences.sql: ${exps.length} 경험 + ${tagRows.length} 태그`)
