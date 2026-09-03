<script setup>
import { ref } from 'vue'
import { strLabel } from '@/lib/matching.js'

/* 목업의 구성을 그대로 가져왔다 — 표 + 누르면 펼쳐지는 근거.
   역량이 10~14개라 블록으로 쌓으면 화면이 길어져 비교가 안 된다.
   표는 같은 축(커버리지·가중치)이 세로로 정렬돼 한눈에 대조된다. */
defineProps({ rows: { type: Array, required: true } })

const CAT = { ROLE: '직무', TECH: '기술', SOFT: '협업', DOMAIN: '산업', VALUE: '인재상' }
const open = ref(new Set())
function toggle(id) {
  const next = new Set(open.value)
  next.has(id) ? next.delete(id) : next.add(id)
  open.value = next
}
</script>

<template>
  <div class="tw">
    <table class="tb">
      <thead>
        <tr>
          <th>요구 역량</th>
          <th class="c">구분</th>
          <th class="cov">커버리지</th>
          <th class="c">근거</th>
          <th class="r">가중치</th>
        </tr>
      </thead>
      <tbody>
        <template v-for="r in rows" :key="r.competencyId">
          <tr class="mrow" :class="{ on: open.has(r.competencyId) }"
              tabindex="0" role="button" :aria-expanded="open.has(r.competencyId)"
              :aria-label="`${r.comp.name} 근거 경험 펼치기`"
              @click="toggle(r.competencyId)"
              @keydown.enter.prevent="toggle(r.competencyId)"
              @keydown.space.prevent="toggle(r.competencyId)">
            <td>
              <b class="nm" :class="{ gap: r.isGap }">{{ r.comp.name }}</b>
            </td>
            <td class="c cat">{{ CAT[r.comp.category] }}</td>
            <td>
              <div class="mt">
                <div class="meter" :class="{ gap: r.isGap }">
                  <i :style="{ width: Math.round(r.score * 100) + '%' }" />
                </div>
                <span class="num pct" :class="{ gap: r.isGap }">{{ Math.round(r.score * 100) }}%</span>
              </div>
            </td>
            <td class="c">
              <span class="num ev" :class="{ gap: !r.evid.length }">
                {{ r.evid.length ? r.evid.length + '건' : '없음' }}
              </span>
            </td>
            <td class="r num wt">{{ r.weight.toFixed(1) }}</td>
          </tr>

          <tr v-if="open.has(r.competencyId)" class="mdet">
            <td colspan="5">
              <template v-if="r.evid.length">
                <p v-for="e in r.evid" :key="e.id" class="dl">
                  <b>{{ e.title }}</b>
                  <span class="tag st">{{ strLabel(e.strength?.[r.competencyId] ?? 0.6) }}</span>
                  <span class="res">{{ e.result }}</span>
                </p>
              </template>
              <p v-else class="dl none">
                이 역량을 태그한 경험이 하나도 없습니다. 경험을 등록하거나, 기존 경험에 이 역량을 태그하세요.
              </p>
              <p class="src">공고 근거 — “{{ r.evidence }}”</p>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
/* 좁은 화면에서 표가 페이지를 밀지 않게 자체 스크롤을 준다 */
.tw { overflow-x: auto; }
.tb { width: 100%; border-collapse: collapse; min-width: 520px; }

th {
  padding: 0 10px 9px; text-align: left;
  border-bottom: 2px solid var(--ink);
  font-family: var(--mono); font-size: 9.5px; font-weight: 500;
  letter-spacing: var(--track-label); text-transform: uppercase; color: var(--muted);
  white-space: nowrap;
}
th:first-child, td:first-child { padding-left: 2px; }
th:last-child,  td:last-child  { padding-right: 2px; }
.c { text-align: center; }
.r { text-align: right; }
.cov { width: 38%; }

td { padding: 11px 10px; border-bottom: 1px solid var(--line-soft); vertical-align: middle; }

.mrow { cursor: pointer; transition: background var(--release) linear; }
.mrow:hover { background: var(--panel-sunken); }
.mrow.on { background: var(--panel-sunken); }
.mrow.on td { border-bottom-color: transparent; }

.nm { font-size: 13px; font-weight: 700; letter-spacing: var(--track-tight); }
.nm.gap { color: var(--gap); }
.cat { font-size: 11px; color: var(--faint); white-space: nowrap; }

.mt { display: flex; align-items: center; gap: 9px; }
.meter { flex: 1; min-width: 60px; height: 5px; background: var(--panel-sunken); border-radius: var(--pill); overflow: hidden; }
.mrow:hover .meter, .mrow.on .meter { background: var(--line); }
.meter i { display: block; height: 100%; background: var(--ink); }
.meter.gap i { background: var(--gap); }
.pct { font-size: 12px; font-weight: 700; flex: none; min-width: 34px; text-align: right; }
.pct.gap { color: var(--gap); }

.ev { font-size: 12px; font-weight: 600; }
.ev.gap { color: var(--gap); }
.wt { font-size: 12px; color: var(--muted); }

/* 펼친 근거 */
.mdet td { background: var(--panel-sunken); padding: 4px 12px 13px; border-bottom: 1px solid var(--line-soft); }
.dl { margin: 0 0 5px; font-size: 12.5px; display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
.dl b { font-weight: 700; }
.st { font-size: 10px; padding: 1px 8px; background: var(--panel); }
.res { color: var(--muted); }
.none { color: var(--gap); font-weight: 600; }
.src { margin: 8px 0 0; font-size: 11.5px; color: var(--faint); }
</style>
